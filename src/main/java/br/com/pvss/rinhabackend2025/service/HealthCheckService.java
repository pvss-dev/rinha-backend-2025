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
    private static final Duration CACHE_DURATION = Duration.ofSeconds(3);

    private final WebClient defaultProcessorClient;
    private final WebClient fallbackProcessorClient;

    private volatile HealthState defaultHealth = new HealthState(true);
    private volatile HealthState fallbackHealth = new HealthState(true);

    public HealthCheckService(
            @Qualifier("defaultProcessorClient") WebClient defaultProcessorClient,
            @Qualifier("fallbackProcessorClient") WebClient fallbackProcessorClient) {
        this.defaultProcessorClient = defaultProcessorClient;
        this.fallbackProcessorClient = fallbackProcessorClient;
    }

    public Mono<ProcessorType> getAvailableProcessor() {
        return checkHealth(ProcessorType.DEFAULT)
                .flatMap(defaultHealthy -> {
                    if (defaultHealthy) {
                        return Mono.just(ProcessorType.DEFAULT);
                    }
                    return checkHealth(ProcessorType.FALLBACK)
                            .map(fallbackHealthy -> fallbackHealthy ? ProcessorType.FALLBACK : ProcessorType.DEFAULT);
                });
    }

    private Mono<Boolean> checkHealth(ProcessorType type) {
        HealthState current = getCurrentHealthState(type);

        if (!current.isExpired()) {
            return Mono.just(current.isHealthy());
        }

        WebClient client = getClientForProcessor(type);

        return client.get()
                .uri("/payments/service-health")
                .retrieve()
                .bodyToMono(HealthResponse.class)
                .timeout(HEALTH_CHECK_TIMEOUT)
                .map(response -> {
                    boolean healthy = !response.failing();
                    updateHealthState(type, healthy);
                    return healthy;
                })
                .onErrorReturn(false)
                .doOnNext(healthy -> updateHealthState(type, healthy));
    }

    private HealthState getCurrentHealthState(ProcessorType type) {
        return type == ProcessorType.DEFAULT ? defaultHealth : fallbackHealth;
    }

    private void updateHealthState(ProcessorType type, boolean healthy) {
        HealthState newState = new HealthState(healthy);
        if (type == ProcessorType.DEFAULT) {
            defaultHealth = newState;
        } else {
            fallbackHealth = newState;
        }
    }

    private WebClient getClientForProcessor(ProcessorType type) {
        return type == ProcessorType.DEFAULT ? defaultProcessorClient : fallbackProcessorClient;
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
            return System.currentTimeMillis() - timestamp > CACHE_DURATION.toMillis();
        }
    }
}