package br.com.pvss.rinhabackend2025.service;

import br.com.pvss.rinhabackend2025.dto.HealthResponse;
import br.com.pvss.rinhabackend2025.dto.ProcessorType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;

@Service
public class HealthCheckService {

    private static final Logger log = LoggerFactory.getLogger(HealthCheckService.class);
    private static final String HEALTH_CHECK_KEY = "health:default:healthy";
    private static final String LOCK_KEY = "health:lock";
    private static final Duration HEALTH_CHECK_TIMEOUT = Duration.ofSeconds(1);
    private static final Duration CACHE_DURATION = Duration.ofSeconds(4);
    private static final Duration LOCK_TTL = Duration.ofSeconds(2);

    private final WebClient defaultProcessorClient;
    private final ReactiveStringRedisTemplate redis;

    public HealthCheckService(
            @Qualifier("defaultProcessorClient") WebClient defaultProcessorClient,
            ReactiveStringRedisTemplate redis
    ) {
        this.defaultProcessorClient = defaultProcessorClient;
        this.redis = redis;
    }

    public Mono<ProcessorType> getAvailableProcessor() {
        return redis.opsForValue().get(HEALTH_CHECK_KEY)
                .map("true"::equals)
                .switchIfEmpty(tryAcquireLockAndCheckHealth())
                .onErrorResume(e -> Mono.just(false))
                .map(isHealthy -> isHealthy ? ProcessorType.DEFAULT : ProcessorType.FALLBACK);
    }

    private Mono<Boolean> tryAcquireLockAndCheckHealth() {
        return redis.opsForValue().setIfAbsent(LOCK_KEY, "1", LOCK_TTL)
                .flatMap(locked -> {
                    if (Boolean.TRUE.equals(locked)) {
                        return performRemoteHealthCheck();
                    }

                    return Mono.delay(Duration.ofMillis(50))
                            .then(redis.opsForValue().get(HEALTH_CHECK_KEY))
                            .map("true"::equals)
                            .switchIfEmpty(Mono.just(false));
                });
    }

    private Mono<Boolean> performRemoteHealthCheck() {
        return defaultProcessorClient.get()
                .uri("/payments/service-health")
                .retrieve()
                .bodyToMono(HealthResponse.class)
                .timeout(HEALTH_CHECK_TIMEOUT)
                .map(response -> !response.failing())
                .publishOn(Schedulers.boundedElastic())
                .doOnSuccess(healthy -> {
                    log.info("Health check do Default atualizado para: {}", healthy ? "SAUDÁVEL" : "FALHANDO");
                    redis.opsForValue().set(HEALTH_CHECK_KEY, String.valueOf(healthy), CACHE_DURATION).subscribe();
                })
                .onErrorResume(e -> {
                    log.warn("Health check do Default falhou: {}. Considerando-o indisponível.", e.getMessage());
                    return redis.opsForValue().set(HEALTH_CHECK_KEY, "false", CACHE_DURATION)
                            .then(Mono.just(false));
                })
                .doFinally(signalType -> redis.opsForValue().delete(LOCK_KEY).subscribe());
    }
}