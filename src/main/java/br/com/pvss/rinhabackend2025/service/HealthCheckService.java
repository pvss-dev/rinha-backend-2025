package br.com.pvss.rinhabackend2025.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class HealthCheckService {

    private final WebClient.Builder webClientBuilder;
    private final RedisTemplate<String, String> redisTemplate;
    public static final String[] PROCESSORS = {"http://payment-processor-default:8080", "http://payment-processor-fallback:8080"};

    @Scheduled(fixedRate = 5000)
    public void checkProcessors() {
        for (String baseUrl : PROCESSORS) {
            long start = System.nanoTime();
            WebClient client = webClientBuilder.baseUrl(baseUrl).build();

            client.get()
                    .uri("/payments/service-health")
                    .retrieve()
                    .bodyToMono(Map.class)
                    .timeout(Duration.ofSeconds(2))
                    .doOnSuccess(responseBody -> {
                        boolean isFailing = (Boolean) responseBody.get("failing");
                        if (!isFailing) {
                            long durationMs = (System.nanoTime() - start) / 1_000_000;
                            redisTemplate.opsForHash().put("processor_status", baseUrl, "healthy|" + durationMs);
                        } else {
                            redisTemplate.opsForHash().put("processor_status", baseUrl, "unhealthy|0");
                        }
                    })
                    .doOnError(error -> {
                        redisTemplate.opsForHash().put("processor_status", baseUrl, "unhealthy|0");
                    })
                    .subscribe();
        }
    }
}