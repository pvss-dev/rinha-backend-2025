package br.com.pvss.rinhabackend2025.service;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@Service
@AllArgsConstructor
public class HealthCheckService {

    private final WebClient.Builder webClientBuilder;
    private final RedisTemplate<String, String> redisTemplate;
    public static final String DEFAULT_PROCESSOR = "http://payment-processor-default:8080";
    public static final String FALLBACK_PROCESSOR = "http://payment-processor-fallback:8080";
    private final Map<String, AtomicLong> lastCallTimes = new ConcurrentHashMap<>();

    @Scheduled(fixedRate = 6000)
    public void checkProcessors() {
        checkProcessor(DEFAULT_PROCESSOR);
        checkProcessor(FALLBACK_PROCESSOR);
    }

    private void checkProcessor(String baseUrl) {
        long currentTime = System.currentTimeMillis();
        AtomicLong lastCall = lastCallTimes.get(baseUrl);

        if (currentTime - lastCall.get() < 5000) {
            return;
        }

        lastCall.set(currentTime);

        long start = System.nanoTime();
        WebClient client = webClientBuilder.baseUrl(baseUrl).build();

        client.get()
                .uri("/payments/service-health")
                .retrieve()
                .bodyToMono(Map.class)
                .timeout(Duration.ofSeconds(3))
                .doOnSuccess(responseBody -> {
                    try {
                        boolean isFailing = (Boolean) responseBody.get("failing");
                        Integer minResponseTime = (Integer) responseBody.get("minResponseTime");

                        if (!isFailing) {
                            long actualResponseTime = (System.nanoTime() - start) / 1_000_000;
                            long effectiveResponseTime = Math.max(actualResponseTime, minResponseTime);

                            String processorKey = baseUrl.contains("default") ? "default" : "fallback";
                            redisTemplate.opsForHash().put("processor_status", processorKey,
                                    "healthy|" + effectiveResponseTime);

                            log.debug("Processor {} is healthy, response time: {}ms", processorKey, effectiveResponseTime);
                        } else {
                            String processorKey = baseUrl.contains("default") ? "default" : "fallback";
                            redisTemplate.opsForHash().put("processor_status", processorKey, "unhealthy|99999");
                            log.warn("Processor {} is failing", processorKey);
                        }
                    } catch (Exception e) {
                        log.error("Error parsing health check response for {}: {}", baseUrl, e.getMessage());
                        handleProcessorError(baseUrl);
                    }
                })
                .doOnError(error -> {
                    if (error instanceof WebClientResponseException.TooManyRequests) {
                        log.warn("Rate limited by processor {}, backing off", baseUrl);
                        lastCall.set(currentTime + 5000);
                    } else {
                        log.error("Health check failed for {}: {}", baseUrl, error.getMessage());
                    }
                    handleProcessorError(baseUrl);
                })
                .subscribe();
    }

    private void handleProcessorError(String baseUrl) {
        String processorKey = baseUrl.contains("default") ? "default" : "fallback";
        redisTemplate.opsForHash().put("processor_status", processorKey, "unhealthy|99999");
    }

    public String getBestProcessor() {
        Map<Object, Object> statuses = redisTemplate.opsForHash().entries("processor_status");

        String defaultStatus = (String) statuses.get("default");
        String fallbackStatus = (String) statuses.get("fallback");

        if (defaultStatus == null && fallbackStatus == null) {
            return DEFAULT_PROCESSOR;
        }

        if (defaultStatus != null && defaultStatus.startsWith("healthy")) {
            return DEFAULT_PROCESSOR;
        }

        if (fallbackStatus != null && fallbackStatus.startsWith("healthy")) {
            return FALLBACK_PROCESSOR;
        }

        return DEFAULT_PROCESSOR;
    }
}