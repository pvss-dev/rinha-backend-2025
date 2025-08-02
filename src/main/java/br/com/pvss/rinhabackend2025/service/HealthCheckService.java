package br.com.pvss.rinhabackend2025.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;

@Service
@RequiredArgsConstructor
public class HealthCheckService {

    private final WebClient.Builder webClientBuilder;
    private final RedisTemplate<String, String> redisTemplate;
    public static final String[] PROCESSORS = {"http://processor-default", "http://processor-fallback"};

    @Scheduled(fixedRate = 5000)
    public void checkProcessors() {
        for (String baseUrl : PROCESSORS) {
            long start = System.nanoTime();
            WebClient client = webClientBuilder.baseUrl(baseUrl).build();
            Mono<Boolean> healthMono = client.get()
                    .uri("/health")
                    .retrieve()
                    .bodyToMono(Void.class)
                    .then(Mono.just(true))
                    .timeout(Duration.ofSeconds(2))
                    .onErrorReturn(false);

            Boolean healthy = healthMono.block();
            long durationMs = (System.nanoTime() - start) / 1_000_000;
            redisTemplate.opsForHash().put("processor_status", baseUrl,
                    healthy + "|" + durationMs);
        }
    }
}