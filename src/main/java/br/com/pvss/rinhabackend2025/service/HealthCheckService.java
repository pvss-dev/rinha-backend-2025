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

    private final WebClient defaultProcessorClient;
    private final WebClient fallbackProcessorClient;
    private final RedisTemplate<String, String> redisTemplate;

    public static final String DEFAULT = "default";
    public static final String FALLBACK = "fallback";
    private final Map<String, AtomicLong> lastCallTimes = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        lastCallTimes.put(DEFAULT, new AtomicLong(0));
        lastCallTimes.put(FALLBACK, new AtomicLong(0));
    }

    @Scheduled(fixedRate = 5100)
    public void checkAll() {
        checkProcessor(DEFAULT, defaultProcessorClient);
        checkProcessor(FALLBACK, fallbackProcessorClient);
    }

    private void checkProcessor(String key, WebClient client) {
        AtomicLong lastCall = lastCallTimes.get(key);
        long now = System.currentTimeMillis();

        if (now - lastCall.get() < 5000) return;

        if (!lastCall.compareAndSet(lastCall.get(), now)) return;

        long startNano = System.nanoTime();
        client.get()
                .uri("/payments/service-health")
                .retrieve()
                .bodyToMono(Map.class)
                .timeout(Duration.ofSeconds(3))
                .doOnSuccess(body -> handleSuccess(key, body, startNano))
                .doOnError(error -> handleError(key, error))
                .subscribe();
    }

    private void handleSuccess(String key, Map<String, Object> body, long startNano) {
        boolean failing = (Boolean) body.getOrDefault("failing", true);
        int minRt = (Integer) body.getOrDefault("minResponseTime", Integer.MAX_VALUE);
        if (!failing) {
            long actual = (System.nanoTime() - startNano) / 1_000_000;
            long rt = Math.max(actual, minRt);
            redisTemplate.opsForHash().put("processor_status", key, "healthy|" + rt);
            log.debug("Processor {} healthy: {}ms", key, rt);
        } else {
            redisTemplate.opsForHash().put("processor_status", key, "unhealthy|99999");
            log.warn("Processor {} failing", key);
        }
    }

    private void handleError(String key, Throwable error) {
        if (error instanceof WebClientResponseException.TooManyRequests) {
            log.warn("Rate limited {}, backing off", key);
            lastCallTimes.get(key).set(System.currentTimeMillis() + 10000);
        } else {
            log.error("Health check error for {}: {}", key, error.toString());
        }
        redisTemplate.opsForHash().put("processor_status", key, "unhealthy|99999");
    }

    public String getBestProcessor() {
        Map<Object, Object> stats = redisTemplate.opsForHash().entries("processor_status");
        String d = (String) stats.get(DEFAULT);
        String f = (String) stats.get(FALLBACK);
        boolean okD = d != null && d.startsWith("healthy");
        boolean okF = f != null && f.startsWith("healthy");
        if (okD && okF) {
            long rtD = Long.parseLong(d.split("\\|")[1]);
            long rtF = Long.parseLong(f.split("\\|")[1]);
            return rtD <= rtF ? DEFAULT : FALLBACK;
        }
        if (okD) return DEFAULT;
        if (okF) return FALLBACK;
        return DEFAULT;
    }
}