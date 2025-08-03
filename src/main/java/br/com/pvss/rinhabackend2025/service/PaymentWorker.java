package br.com.pvss.rinhabackend2025.service;

import br.com.pvss.rinhabackend2025.client.PaymentProcessorClient;
import br.com.pvss.rinhabackend2025.dto.PaymentDto;
import br.com.pvss.rinhabackend2025.dto.PaymentRequestDto;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.SessionCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentWorker {

    private final StringRedisTemplate redisTemplate;
    private final PaymentProcessorClient processorClient;
    private final ObjectMapper objectMapper;
    private final ExecutorService workerPool = Executors.newFixedThreadPool(16);

    @PostConstruct
    public void init() {
        log.info("Starting PaymentWorker pool...");
        for (int i = 0; i < 16; i++) {
            workerPool.submit(this::processQueue);
        }
    }

    @SneakyThrows
    public void processQueue() {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                String paymentJson = redisTemplate.opsForList().rightPop(PaymentService.PAYMENT_QUEUE, 5, TimeUnit.SECONDS);

                if (paymentJson != null) {
                    PaymentDto dto = objectMapper.readValue(paymentJson, PaymentDto.class);
                    processPayment(dto, paymentJson);
                }
            } catch (Exception e) {
                log.error("Error in worker thread, restarting loop.", e);
                Thread.sleep(1000);
            }
        }
    }

    private void processPayment(PaymentDto dto, String originalJson) {
        String chosenProcessor = chooseProcessor();

        PaymentRequestDto requestDto = new PaymentRequestDto(
                dto.correlationId(),
                dto.amount(),
                Instant.now().toString()
        );

        processorClient.process(requestDto, chosenProcessor)
                .doOnSuccess(v -> handleSuccess(chosenProcessor.contains("default"), dto.amount()))
                .doOnError(e -> {
                    log.warn("Processing failed for correlationId {}. Re-queueing.", dto.correlationId());
                    redisTemplate.opsForList().leftPush(PaymentService.PAYMENT_QUEUE, originalJson);
                })
                .subscribe();
    }

    private void handleSuccess(boolean isDefaultProcessor, BigDecimal amount) {
        String processorType = isDefaultProcessor ? "default" : "fallback";
        String reqKey = "rinha:summary:" + processorType + ":requests";
        String amountKey = "rinha:summary:" + processorType + ":amount";

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
    }

    private String chooseProcessor() {
        Map<Object, Object> statuses = redisTemplate.opsForHash().entries("processor_status");
        String defaultUrl = "http://payment-processor-default:8080";
        String fallbackUrl = "http://payment-processor-fallback:8080";

        return statuses.entrySet().stream()
                .map(e -> Map.entry((String) e.getKey(), (String) e.getValue()))
                .filter(entry -> entry.getValue().startsWith("healthy"))
                .map(entry -> Map.entry(entry.getKey(), Long.parseLong(entry.getValue().split("\\|")[1])))
                .min(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(defaultUrl);
    }
}