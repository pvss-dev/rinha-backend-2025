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

@Service
public class HealthCheckService {

    private static final Logger log = LoggerFactory.getLogger(HealthCheckService.class);
    private static final Duration HEALTH_CHECK_TIMEOUT = Duration.ofSeconds(1);
    private static final Duration CACHE_DURATION = Duration.ofSeconds(6);

    private final WebClient defaultProcessorClient;
    private volatile HealthState defaultHealth = new HealthState(true);

    public HealthCheckService(@Qualifier("defaultProcessorClient") WebClient defaultProcessorClient) {
        this.defaultProcessorClient = defaultProcessorClient;
    }

    public Mono<ProcessorType> getAvailableProcessor() {
        return checkHealth()
                .map(isDefaultHealthy -> {
                    if (Boolean.TRUE.equals(isDefaultHealthy)) {
                        return ProcessorType.DEFAULT;
                    }
                    return ProcessorType.FALLBACK;
                });
    }

    private Mono<Boolean> checkHealth() {
        if (!defaultHealth.isExpired()) {
            return Mono.just(defaultHealth.isHealthy());
        }

        return defaultProcessorClient.get()
                .uri("/payments/service-health")
                .retrieve()
                .bodyToMono(HealthResponse.class)
                .timeout(HEALTH_CHECK_TIMEOUT)
                .map(response -> !response.failing())
                .doOnSuccess(healthy -> {
                    log.info("Health check do Default atualizado para: {}", healthy ? "SAUDÁVEL" : "FALHANDO");
                    this.defaultHealth = new HealthState(healthy);
                })
                .onErrorResume(e -> {
                    log.warn("Health check do Default falhou: {}. Considerando-o indisponível.", e.getMessage());
                    this.defaultHealth = new HealthState(false);
                    return Mono.just(false);
                });
    }

    private static class HealthState {
        private final boolean healthy;
        private final long timestamp;

        HealthState(boolean healthy) {
            this.healthy = healthy;
            this.timestamp = System.currentTimeMillis();
        }

        boolean isHealthy() {
            return healthy;
        }

        boolean isExpired() {
            return (System.currentTimeMillis() - timestamp) > CACHE_DURATION.toMillis();
        }
    }
}