package br.com.pvss.rinhabackend2025.service;

import br.com.pvss.rinhabackend2025.dto.ProcessorType;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class HealthCheckService {
    private static final Logger log = LoggerFactory.getLogger(HealthCheckService.class);
    private final WebClient defaultProcessorClient;
    private final WebClient fallbackProcessorClient;
    private final Map<ProcessorType, CachedHealth> cache = new ConcurrentHashMap<>();
    private static final Duration CACHE_DURATION = Duration.ofSeconds(6);
    private static final Duration HEALTH_CHECK_TIMEOUT = Duration.ofSeconds(3);
    private final Map<ProcessorType, Long> lastHealthCheck = new ConcurrentHashMap<>();
    private static final Duration MIN_INTERVAL_BETWEEN_CHECKS = Duration.ofSeconds(5);

    public HealthCheckService(
            @Qualifier("defaultProcessorClient") WebClient defaultProcessorClient,
            @Qualifier("fallbackProcessorClient") WebClient fallbackProcessorClient) {
        this.defaultProcessorClient = defaultProcessorClient;
        this.fallbackProcessorClient = fallbackProcessorClient;
    }

    public Mono<ProcessorType> getAvailableProcessor() {
        return Mono.defer(() -> {
            List<ProcessorType> preference = List.of(ProcessorType.DEFAULT, ProcessorType.FALLBACK);

            return Flux.fromIterable(preference)
                    .flatMap(this::getHealthWithRateLimit)
                    .filter(pair -> {
                        boolean isHealthy = pair.getRight();
                        if (!isHealthy) {
                            log.debug("Processador {} não está saudável", pair.getLeft());
                        }
                        return isHealthy;
                    })
                    .map(Pair::getLeft)
                    .next()
                    .doOnSuccess(processor -> {
                        if (processor != null) {
                            log.debug("Processador selecionado: {}", processor);
                        }
                    })
                    .switchIfEmpty(Mono.defer(() -> {
                        log.warn("Nenhum processador disponível no cache. Usando estratégia de fallback conservadora...");
                        return fallbackStrategy(preference);
                    }));
        });
    }

    private Mono<ProcessorType> fallbackStrategy(List<ProcessorType> preference) {
        return Flux.fromIterable(preference)
                .filter(this::wasNotRecentlyFailing)
                .next()
                .switchIfEmpty(Mono.defer(() -> {
                    log.warn("Todos os processadores falharam recentemente. Usando DEFAULT como fallback.");
                    return Mono.just(ProcessorType.DEFAULT);
                }))
                .doOnNext(processor -> log.info("Usando processador {} como fallback", processor));
    }

    private boolean wasNotRecentlyFailing(ProcessorType type) {
        CachedHealth cached = cache.get(type);
        if (cached == null) {
            return true;
        }

        if (!cached.expired() && cached.ok) {
            return true;
        }

        if (cached.expired()) {
            return true;
        }

        return false;
    }

    private Mono<Pair<ProcessorType, Boolean>> getHealthWithRateLimit(ProcessorType type) {
        CachedHealth cached = cache.get(type);

        if (cached != null && !cached.expired()) {
            log.debug("Usando cache para processador {}: {}", type, cached.ok);
            return Mono.just(Pair.of(type, cached.ok));
        }

        if (canMakeHealthCheck(type)) {
            return forceHealthCheck(type);
        } else {
            boolean lastKnownStatus = cached != null ? cached.ok : false;
            log.debug("Rate limit ativo para {}. Usando último status conhecido: {}", type, lastKnownStatus);
            boolean assumedStatus = cached != null && cached.ok ? true : lastKnownStatus;

            return Mono.just(Pair.of(type, assumedStatus));
        }
    }

    private boolean canMakeHealthCheck(ProcessorType type) {
        Long lastCheck = lastHealthCheck.get(type);
        if (lastCheck == null) {
            return true;
        }

        long timeSinceLastCheck = System.currentTimeMillis() - lastCheck;
        boolean canCheck = timeSinceLastCheck >= MIN_INTERVAL_BETWEEN_CHECKS.toMillis();

        if (!canCheck) {
            long waitTime = MIN_INTERVAL_BETWEEN_CHECKS.toMillis() - timeSinceLastCheck;
            log.debug("Rate limit ativo para {}. Aguardar mais {}ms", type, waitTime);
        }

        return canCheck;
    }

    private Mono<Pair<ProcessorType, Boolean>> forceHealthCheck(ProcessorType type) {
        log.debug("Verificando saúde do processador: {}", type);
        lastHealthCheck.put(type, System.currentTimeMillis());

        WebClient client = getWebClientForProcessor(type);

        return client.get()
                .uri("/payments/service-health")
                .retrieve()
                .bodyToMono(HealthResponse.class)
                .timeout(HEALTH_CHECK_TIMEOUT)
                .map(resp -> {
                    boolean ok = !resp.failing();
                    cache.put(type, new CachedHealth(ok));
                    log.debug("Health check para {}: failing={}, ok={}", type, resp.failing(), ok);
                    return Pair.of(type, ok);
                })
                .onErrorResume(e -> {
                    log.warn("Erro no health check para processador {}: {}", type, e.getMessage());
                    cache.put(type, new CachedHealth(false, Duration.ofSeconds(2)));
                    return Mono.just(Pair.of(type, false));
                });
    }

    private WebClient getWebClientForProcessor(ProcessorType type) {
        return switch (type) {
            case DEFAULT -> defaultProcessorClient;
            case FALLBACK -> fallbackProcessorClient;
        };
    }

    private static class CachedHealth {
        private final boolean ok;
        private final long timestamp;
        private final Duration cacheDuration;

        CachedHealth(boolean ok) {
            this(ok, CACHE_DURATION);
        }

        CachedHealth(boolean ok, Duration cacheDuration) {
            this.ok = ok;
            this.timestamp = System.currentTimeMillis();
            this.cacheDuration = cacheDuration;
        }

        boolean expired() {
            return System.currentTimeMillis() - timestamp > cacheDuration.toMillis();
        }
    }

    private record HealthResponse(boolean failing, int minResponseTime) {
    }
}