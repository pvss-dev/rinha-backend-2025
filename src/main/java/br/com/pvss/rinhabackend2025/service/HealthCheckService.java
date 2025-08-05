package br.com.pvss.rinhabackend2025.service;

import br.com.pvss.rinhabackend2025.dto.HealthResponse;
import br.com.pvss.rinhabackend2025.dto.ProcessorType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class HealthCheckService {

    private static final Logger log = LoggerFactory.getLogger(HealthCheckService.class);
    private final WebClient defaultProcessorClient;
    private final WebClient fallbackProcessorClient;
    private final Map<ProcessorType, CachedHealth> healthCache = new ConcurrentHashMap<>();
    private final Map<ProcessorType, Long> lastHealthCheckTimestamps = new ConcurrentHashMap<>();
    private static final Duration CACHE_DURATION = Duration.ofSeconds(6);
    private static final Duration FAILED_CACHE_DURATION = Duration.ofSeconds(2);
    private static final Duration HEALTH_CHECK_TIMEOUT = Duration.ofSeconds(3);
    private static final Duration MIN_INTERVAL_BETWEEN_CHECKS = Duration.ofSeconds(5);

    public HealthCheckService(
            @Qualifier("defaultProcessorClient") WebClient defaultProcessorClient,
            @Qualifier("fallbackProcessorClient") WebClient fallbackProcessorClient) {
        this.defaultProcessorClient = defaultProcessorClient;
        this.fallbackProcessorClient = fallbackProcessorClient;
    }

    public Mono<ProcessorType> getAvailableProcessor() {
        log.debug("Iniciando seleção de processador...");
        return checkProcessor(ProcessorType.DEFAULT)
                .filter(isHealthy -> isHealthy)
                .map(isHealthy -> ProcessorType.DEFAULT)
                .doOnNext(type -> log.debug("Processador DEFAULT selecionado por estar saudável."))
                .switchIfEmpty(Mono.defer(() -> {
                    log.debug("Processador DEFAULT insalubre ou em falha. Verificando FALLBACK...");
                    return checkProcessor(ProcessorType.FALLBACK)
                            .filter(isHealthy -> isHealthy)
                            .map(isHealthy -> ProcessorType.FALLBACK)
                            .doOnNext(type -> log.debug("Processador FALLBACK selecionado."));
                }))
                .switchIfEmpty(Mono.defer(() -> {
                    log.warn("Ambos processadores parecem insalubres. Tentando DEFAULT como último recurso.");
                    return Mono.just(ProcessorType.DEFAULT);
                }));
    }

    private Mono<Boolean> checkProcessor(ProcessorType type) {
        CachedHealth cached = healthCache.get(type);
        if (cached != null && !cached.isExpired()) {
            log.trace("Usando cache para health-check de {}: {}", type, cached.isOk());
            return Mono.just(cached.isOk());
        }

        if (!canMakeHealthCheck(type)) {
            boolean lastKnownStatus = cached != null && cached.isOk();
            log.debug("Rate limit ativo para {}. Usando último status conhecido: {}", type, lastKnownStatus);
            return Mono.just(lastKnownStatus);
        }

        return forceHealthCheck(type);
    }

    private Mono<Boolean> forceHealthCheck(ProcessorType type) {
        log.debug("Forçando health check para o processador: {}", type);
        lastHealthCheckTimestamps.put(type, System.currentTimeMillis());

        WebClient client = getWebClientForProcessor(type);

        return client.get()
                .uri("/payments/service-health")
                .retrieve()
                .bodyToMono(HealthResponse.class)
                .timeout(HEALTH_CHECK_TIMEOUT)
                .map(response -> {
                    boolean isOk = !response.failing();
                    healthCache.put(type, new CachedHealth(isOk, CACHE_DURATION));
                    log.debug("Resultado do Health check para {}: saudável={}", type, isOk);
                    return isOk;
                })
                .onErrorResume(e -> {
                    log.warn("Erro no health check para o processador {}: {}. Marcando como insalubre.", type, e.getMessage());
                    healthCache.put(type, new CachedHealth(false, FAILED_CACHE_DURATION));
                    return Mono.just(false);
                });
    }

    private boolean canMakeHealthCheck(ProcessorType type) {
        Long lastCheck = lastHealthCheckTimestamps.get(type);
        if (lastCheck == null) {
            return true;
        }
        return System.currentTimeMillis() - lastCheck > MIN_INTERVAL_BETWEEN_CHECKS.toMillis();
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

        CachedHealth(boolean ok, Duration cacheDuration) {
            this.ok = ok;
            this.timestamp = System.currentTimeMillis();
            this.cacheDuration = cacheDuration;
        }

        boolean isOk() {
            return ok;
        }

        boolean isExpired() {
            return System.currentTimeMillis() - timestamp > cacheDuration.toMillis();
        }
    }
}