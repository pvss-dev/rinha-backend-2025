package br.com.pvss.rinhabackend2025.service;

import jakarta.annotation.PostConstruct;
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

    @PostConstruct
    public void init() {
        lastCallTimes.put(DEFAULT_PROCESSOR, new AtomicLong(0));
        lastCallTimes.put(FALLBACK_PROCESSOR, new AtomicLong(0));
    }

    @Scheduled(fixedRate = 5100)
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

        if (!lastCall.compareAndSet(lastCall.get(), currentTime)) {
            return;
        }

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
                        String processorKey = baseUrl.contains("default") ? "default" : "fallback";

                        if (!isFailing) {
                            long actualResponseTime = (System.nanoTime() - start) / 1_000_000;
                            long effectiveResponseTime = Math.max(actualResponseTime, minResponseTime);

                            redisTemplate.opsForHash().put("processor_status", processorKey, "healthy|" + effectiveResponseTime);
                            log.debug("Processor {} is healthy, response time: {}ms", processorKey, effectiveResponseTime);
                        } else {
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
                        lastCall.set(System.currentTimeMillis() + 5000);
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

        boolean isDefaultHealthy = defaultStatus != null && defaultStatus.startsWith("healthy");
        boolean isFallbackHealthy = fallbackStatus != null && fallbackStatus.startsWith("healthy");

        if (isDefaultHealthy && isFallbackHealthy) {
            long defaultTime = Long.parseLong(defaultStatus.split("\\|")[1]);
            long fallbackTime = Long.parseLong(fallbackStatus.split("\\|")[1]);
            return defaultTime <= fallbackTime ? DEFAULT_PROCESSOR : FALLBACK_PROCESSOR;
        }

        if (isDefaultHealthy) {
            return DEFAULT_PROCESSOR;
        }

        if (isFallbackHealthy) {
            return FALLBACK_PROCESSOR;
        }

        return DEFAULT_PROCESSOR;
    }
}