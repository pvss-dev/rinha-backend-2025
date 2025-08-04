package br.com.pvss.rinhabackend2025.service;

import br.com.pvss.rinhabackend2025.dto.ProcessorType;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class HealthCheckService {
    private final WebClient webClient;
    private final Map<ProcessorType, CachedHealth> cache = new ConcurrentHashMap<>();

    public HealthCheckService(WebClient.Builder builder) {
        this.webClient = builder.build();
    }

    public Mono<ProcessorType> getAvailableProcessor() {
        return Mono.defer(() -> {
            List<ProcessorType> preference = List.of(ProcessorType.DEFAULT, ProcessorType.FALLBACK);

            return Flux.fromIterable(preference)
                    .flatMap(this::getHealth, 1)
                    .filter(Pair::getRight)
                    .map(Pair::getLeft)
                    .next()
                    .switchIfEmpty(Mono.error(new IllegalStateException("Nenhum processador disponível")));
        });
    }

    private Mono<Pair<ProcessorType, Boolean>> getHealth(ProcessorType type) {
        CachedHealth cached = cache.get(type);
        if (cached != null && !cached.expired()) {
            return Mono.just(Pair.of(type, cached.ok));
        }

        return webClient.get()
                .uri(type.getBaseUrl() + "/payments/service-health")
                .retrieve()
                .bodyToMono(HealthResponse.class)
                .map(resp -> {
                    boolean ok = !resp.failing();
                    cache.put(type, new CachedHealth(ok));
                    return Pair.of(type, ok);
                })
                .onErrorResume(e -> Mono.just(Pair.of(type, false)));
    }

    private static class CachedHealth {
        private final boolean ok;
        private final long timestamp;

        CachedHealth(boolean ok) {
            this.ok = ok;
            this.timestamp = System.currentTimeMillis();
        }

        boolean expired() {
            return System.currentTimeMillis() - timestamp > 5000;
        }
    }

    private record HealthResponse(boolean failing, int minResponseTime) {
    }
}