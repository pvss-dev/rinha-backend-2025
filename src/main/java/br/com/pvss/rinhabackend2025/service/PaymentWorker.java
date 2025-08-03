package br.com.pvss.rinhabackend2025.service;

import br.com.pvss.rinhabackend2025.client.PaymentProcessorClient;
import br.com.pvss.rinhabackend2025.dto.PaymentDto;
import br.com.pvss.rinhabackend2025.dto.PaymentRequestDto;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.util.retry.Retry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import com.fasterxml.jackson.core.JsonProcessingException;

@Component
public class PaymentWorker implements DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(PaymentWorker.class);
    private final StringRedisTemplate redisTemplate;
    private final PaymentProcessorClient processorClient;
    private final ObjectMapper objectMapper;
    private final HealthCheckService healthCheckService;
    private final WebClient defaultProcessorClient;
    private final WebClient fallbackProcessorClient;
    private final ExecutorService workerPool = Executors.newFixedThreadPool(8);

    public PaymentWorker(
            StringRedisTemplate redisTemplate,
            PaymentProcessorClient processorClient,
            ObjectMapper objectMapper,
            HealthCheckService healthCheckService,
            @Qualifier("defaultProcessorClient") WebClient defaultProcessorClient,
            @Qualifier("fallbackProcessorClient") WebClient fallbackProcessorClient
    ) {
        this.redisTemplate = redisTemplate;
        this.processorClient = processorClient;
        this.objectMapper = objectMapper;
        this.healthCheckService = healthCheckService;
        this.defaultProcessorClient = defaultProcessorClient;
        this.fallbackProcessorClient = fallbackProcessorClient;
    }

    @PostConstruct
    public void init() {
        log.info("Starting PaymentWorker pool...");
        for (int i = 0; i < 8; i++) {
            workerPool.submit(this::processQueue);
        }
    }

    private void processQueue() {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                String paymentJson = redisTemplate.opsForList()
                        .rightPop(PaymentService.PAYMENT_QUEUE, 1, TimeUnit.SECONDS);

                if (paymentJson != null) {
                    PaymentDto dto = objectMapper.readValue(paymentJson, PaymentDto.class);
                    processPayment(dto);
                }
            } catch (Exception e) {
                log.error("Error in worker thread, restarting loop: {}", e.getMessage());
                try {
                    Thread.sleep(100);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
    }

    private void processPayment(PaymentDto dto) {
        WebClient client = healthCheckService.getBestProcessor().equals(HealthCheckService.DEFAULT)
                ? defaultProcessorClient
                : fallbackProcessorClient;

        Instant ts = Instant.now();
        PaymentRequestDto req = new PaymentRequestDto(
                dto.correlationId(),
                dto.amount(),
                ts.toString()
        );

        try {
            processorClient.process(req, client)
                    .retryWhen(Retry.backoff(2, Duration.ofMillis(100)).jitter(0.5))
                    .block(Duration.ofSeconds(10));
            recordSuccess(client == defaultProcessorClient, dto, ts);
            return;
        } catch (Exception e) {
            log.warn("Primary processor failed for {}: {}", dto.correlationId(), e.getMessage());
        }

        try {
            WebClient alt = (client == defaultProcessorClient)
                    ? fallbackProcessorClient
                    : defaultProcessorClient;

            processorClient.process(req, alt)
                    .retryWhen(Retry.backoff(1, Duration.ofMillis(50)))
                    .block(Duration.ofSeconds(5));
            recordSuccess(alt == defaultProcessorClient, dto, ts);
        } catch (Exception e2) {
            log.error("Both processors failed for {} – requeueing", dto.correlationId());
            requeuePayment(dto);
        }
    }

    private void recordSuccess(boolean usedDefault, PaymentDto dto, Instant ts) {
        String key = "rinha:payments:" + (usedDefault ? "default" : "fallback");
        String val = dto.correlationId() + ":" + dto.amount().toPlainString();
        try {
            redisTemplate.opsForZSet().add(key, val, ts.toEpochMilli());
        } catch (Exception e) {
            log.error("Failed updating Redis summary for {}: {}", dto.correlationId(), e.getMessage());
        }
    }

    private void requeuePayment(PaymentDto dto) {
        try {
            String json = objectMapper.writeValueAsString(dto);
            redisTemplate.opsForList()
                    .leftPush(PaymentService.PAYMENT_QUEUE, json);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Erro ao serializar PaymentDto para JSON", e);
        }
    }

    @Override
    public void destroy() {
        log.info("Shutting down PaymentWorker pool...");
        workerPool.shutdown();
        try {
            if (!workerPool.awaitTermination(5, TimeUnit.SECONDS)) {
                log.warn("Forcing shutdown of worker pool...");
                workerPool.shutdownNow();
            }
        } catch (InterruptedException e) {
            workerPool.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}