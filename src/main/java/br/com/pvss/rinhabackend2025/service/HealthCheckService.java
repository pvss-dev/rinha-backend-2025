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
    private static final Duration CACHE_DURATION = Duration.ofSeconds(5);
    private static final Duration HEALTH_CHECK_TIMEOUT = Duration.ofSeconds(2);

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
                    .flatMap(this::getHealth)
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
                        cache.clear();
                        return tryWithoutCache(preference);
                    }));
        });
    }

    private Mono<ProcessorType> tryWithoutCache(List<ProcessorType> preference) {
        return Flux.fromIterable(preference)
                .flatMap(this::forceHealthCheck)
                .filter(Pair::getRight)
                .map(Pair::getLeft)
                .next()
                .switchIfEmpty(Mono.error(new IllegalStateException(
                        "Nenhum processador disponível após limpeza do cache. Processadores testados: " + preference
                )));
    }

    private Mono<Pair<ProcessorType, Boolean>> getHealth(ProcessorType type) {
        CachedHealth cached = cache.get(type);
        if (cached != null && !cached.expired()) {
            log.debug("Usando cache para processador {}: {}", type, cached.ok);
            return Mono.just(Pair.of(type, cached.ok));
        }

        return forceHealthCheck(type);
    }

    private Mono<Pair<ProcessorType, Boolean>> forceHealthCheck(ProcessorType type) {
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
                    cache.put(type, new CachedHealth(false, Duration.ofSeconds(1)));
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