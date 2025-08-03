package br.com.pvss.rinhabackend2025.service;

import br.com.pvss.rinhabackend2025.client.PaymentProcessorClient;
import br.com.pvss.rinhabackend2025.dto.PaymentDto;
import br.com.pvss.rinhabackend2025.dto.PaymentRequestDto;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import reactor.util.retry.Retry;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentWorker implements DisposableBean {

    private final StringRedisTemplate redisTemplate;
    private final PaymentProcessorClient processorClient;
    private final ObjectMapper objectMapper;
    private final HealthCheckService healthCheckService;
    private final ExecutorService workerPool = Executors.newFixedThreadPool(8);
    private volatile boolean isRedisReady = false;

    @PostConstruct
    public void init() {
        log.info("Starting PaymentWorker pool...");

        waitForRedis();

        for (int i = 0; i < 8; i++) {
            workerPool.submit(this::processQueue);
        }
    }

    private void waitForRedis() {
        log.info("Waiting for Redis to be ready...");
        for (int i = 0; i < 30; i++) {
            try {
                redisTemplate.opsForValue().set("health-check", "ok");
                redisTemplate.delete("health-check");
                isRedisReady = true;
                log.info("Redis is ready!");
                return;
            } catch (Exception e) {
                log.warn("Redis not ready yet, attempt {}/30: {}", i + 1, e.getMessage());
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
        log.error("Redis failed to become ready after 30 seconds!");
    }

    @SneakyThrows
    public void processQueue() {
        while (!isRedisReady && !Thread.currentThread().isInterrupted()) {
            try {
                String paymentJson = redisTemplate.opsForList().rightPop(
                        PaymentService.PAYMENT_QUEUE, 0, TimeUnit.SECONDS);

                if (paymentJson != null) {
                    PaymentDto dto = objectMapper.readValue(paymentJson, PaymentDto.class);
                    processPayment(dto);
                }
            } catch (Exception e) {
                if (e instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                    break;
                }
                log.error("Error in worker thread, restarting loop: {}", e.getMessage());
                Thread.sleep(100);
            }
        }
    }

    private void processPayment(PaymentDto dto) {
        String chosenProcessor = healthCheckService.getBestProcessor();
        Instant requestTime = Instant.now();

        PaymentRequestDto requestDto = new PaymentRequestDto(
                dto.correlationId(),
                dto.amount(),
                requestTime.toString()
        );

        try {
            processorClient.process(requestDto, chosenProcessor)
                    .retryWhen(Retry.backoff(2, Duration.ofMillis(100)).jitter(0.5))
                    .block(Duration.ofSeconds(10));

            boolean isDefaultProcessor = chosenProcessor.contains("default");
            handleSuccess(isDefaultProcessor, dto.correlationId(), dto.amount(), requestTime);
        } catch (Exception e) {
            log.warn("Processing failed for payment {}: {}", dto.correlationId(), e.getMessage());

            String alternativeProcessor = chosenProcessor.contains("default")
                    ? HealthCheckService.FALLBACK_PROCESSOR
                    : HealthCheckService.DEFAULT_PROCESSOR;

            try {
                processorClient.process(requestDto, alternativeProcessor)
                        .retryWhen(Retry.backoff(1, Duration.ofMillis(50)))
                        .block(Duration.ofSeconds(5));

                boolean isDefaultProcessor = alternativeProcessor.contains("default");
                handleSuccess(isDefaultProcessor, dto.correlationId(), dto.amount(), requestTime);

            } catch (Exception e2) {
                log.error("Payment {} failed on both processors, dropping", dto.correlationId());
            }
        }
    }

    private void handleSuccess(boolean isDefaultProcessor, String correlationId, BigDecimal amount, Instant requestTime) {
        String processorType = isDefaultProcessor ? "default" : "fallback";
        String key = "rinha:payments:" + processorType;
        long timestamp = requestTime.toEpochMilli();

        String value = correlationId + ":" + amount.toPlainString();

        try {
            redisTemplate.opsForZSet().add(key, value, timestamp);
        } catch (Exception e) {
            log.error("Failed to update summary statistics in Redis ZSet: {}", e.getMessage());
        }
    }

    @SneakyThrows
    private void requeuePayment(PaymentDto dto) {
        String paymentJson = objectMapper.writeValueAsString(dto);
        redisTemplate.opsForList().leftPush(PaymentService.PAYMENT_QUEUE, paymentJson);
    }


    @Override
    public void destroy() {
        log.info("Shutting down PaymentWorker pool...");
        workerPool.shutdown();
        try {
            if (!workerPool.awaitTermination(5, TimeUnit.SECONDS)) {
                log.warn("Worker pool did not terminate in 5 seconds. Forcing shutdown...");
                workerPool.shutdownNow();
            }
        } catch (InterruptedException e) {
            workerPool.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}