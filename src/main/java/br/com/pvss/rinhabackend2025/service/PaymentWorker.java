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
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.SessionCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentWorker implements DisposableBean {

    private final StringRedisTemplate redisTemplate;
    private final PaymentProcessorClient processorClient;
    private final ObjectMapper objectMapper;
    private final HealthCheckService healthCheckService;
    private final ExecutorService workerPool = Executors.newFixedThreadPool(12);
    private final AtomicInteger retryCount = new AtomicInteger(0);

    @PostConstruct
    public void init() {
        log.info("Starting PaymentWorker pool with 12 threads...");
        for (int i = 0; i < 12; i++) {
            workerPool.submit(this::processQueue);
        }
    }

    @SneakyThrows
    public void processQueue() {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                String paymentJson = redisTemplate.opsForList().rightPop(
                        PaymentService.PAYMENT_QUEUE, 3, TimeUnit.SECONDS);

                if (paymentJson != null) {
                    PaymentDto dto = objectMapper.readValue(paymentJson, PaymentDto.class);
                    processPayment(dto);
                }
            } catch (Exception e) {
                log.error("Error in worker thread: {}", e.getMessage());
                Thread.sleep(1000);
            }
        }
    }

    private void processPayment(PaymentDto dto) {
        String chosenProcessor = healthCheckService.getBestProcessor();

        PaymentRequestDto requestDto = new PaymentRequestDto(
                dto.correlationId(),
                dto.amount(),
                Instant.now().toString()
        );

        processorClient.process(requestDto, chosenProcessor)
                .doOnSuccess(v -> {
                    boolean isDefaultProcessor = chosenProcessor.contains("default");
                    handleSuccess(isDefaultProcessor, dto.amount());
                    log.debug("Payment {} processed successfully using {}",
                            dto.correlationId(), isDefaultProcessor ? "default" : "fallback");
                })
                .doOnError(e -> {
                    int currentRetries = retryCount.incrementAndGet();
                    if (currentRetries % 100 == 0) {
                        log.warn("Processing failures count: {}", currentRetries);
                    }

                    try {
                        String paymentJson = objectMapper.writeValueAsString(dto);
                        redisTemplate.opsForList().leftPush(PaymentService.PAYMENT_QUEUE, paymentJson);
                    } catch (Exception ex) {
                        log.error("Failed to re-queue payment {}: {}", dto.correlationId(), ex.getMessage());
                    }
                })
                .subscribe();
    }

    private void handleSuccess(boolean isDefaultProcessor, BigDecimal amount) {
        String processorType = isDefaultProcessor ? "default" : "fallback";
        String reqKey = "rinha:summary:" + processorType + ":requests";
        String amountKey = "rinha:summary:" + processorType + ":amount";

        try {
            redisTemplate.execute(new SessionCallback<List<Object>>() {
                @Override
                public <K, V> List<Object> execute(RedisOperations<K, V> operations) throws DataAccessException {
                    StringRedisTemplate stringOps = (StringRedisTemplate) operations;

                    stringOps.multi();
                    stringOps.opsForValue().increment(reqKey);
                    stringOps.opsForValue().increment(amountKey, amount.doubleValue());
                    return stringOps.exec();
                }
            });
        } catch (Exception e) {
            log.error("Failed to update summary statistics: {}", e.getMessage());
        }
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