package br.com.pvss.rinhabackend2025.service;

import br.com.pvss.rinhabackend2025.dto.HealthResponse;
import br.com.pvss.rinhabackend2025.dto.ProcessorType;
import br.com.pvss.rinhabackend2025.model.HealthCache;
import br.com.pvss.rinhabackend2025.model.HealthLock;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Service
public class HealthCheckService {

    private static final Duration LOCK_TTL = Duration.ofSeconds(5);
    private static final Duration CACHE_TTL = Duration.ofSeconds(5);
    private static final String DEFAULT_ID = "default";
    private static final String FALLBACK_ID = "fallback";

    private final ReactiveMongoTemplate mongo;
    private final WebClient ppDefault;
    private final WebClient ppFallback;
    private final String hostId = System.getenv().getOrDefault("HOSTNAME", UUID.randomUUID().toString());

    public HealthCheckService(
            ReactiveMongoTemplate mongo,
            @Qualifier("defaultProcessorClient") WebClient ppDefault,
            @Qualifier("fallbackProcessorClient") WebClient ppFallback
    ) {
        this.mongo = mongo;
        this.ppDefault = ppDefault;
        this.ppFallback = ppFallback;
    }

    public Mono<ProcessorType> getAvailableProcessor() {
        return getProcessorHealth(DEFAULT_ID, ppDefault)
                .flatMap(isDefaultHealthy -> {
                    if (isDefaultHealthy) {
                        return Mono.just(ProcessorType.DEFAULT);
                    }
                    return getProcessorHealth(FALLBACK_ID, ppFallback).mapNotNull(isFallbackHealthy -> isFallbackHealthy ? ProcessorType.FALLBACK : null);
                })
                .defaultIfEmpty(ProcessorType.FALLBACK);
    }

    private Mono<Boolean> getProcessorHealth(String processorId, WebClient client) {
        return mongo.findById(processorId, HealthCache.class)
                .filter(c -> c.validUntil() != null && c.validUntil().isAfter(Instant.now()))
                .map(HealthCache::healthy)
                .switchIfEmpty(
                        lockAndRefreshHealth(processorId, client)
                                .retryWhen(Retry.backoff(3, Duration.ofMillis(20)).filter(e -> e instanceof DuplicateKeyException))
                );
    }

    private Mono<Boolean> lockAndRefreshHealth(String processorId, WebClient client) {
        Instant now = Instant.now();
        Instant lease = now.plus(LOCK_TTL);

        Query q = new Query(
                Criteria.where("_id").is(processorId)
                        .orOperator(
                                Criteria.where("expiresAt").lt(now),
                                Criteria.where("expiresAt").exists(false)
                        )
        );
        Update u = new Update()
                .set("expiresAt", lease)
                .set("owner", hostId);

        FindAndModifyOptions options = FindAndModifyOptions.options()
                .upsert(true)
                .returnNew(true);

        return mongo.findAndModify(q, u, options, HealthLock.class)
                .flatMap(lock -> {
                    boolean iAmOwner = hostId.equals(lock.owner());
                    if (!iAmOwner) {
                        return Mono.delay(Duration.ofMillis(50))
                                .then(mongo.findById(processorId, HealthCache.class))
                                .map(c -> c != null && c.healthy())
                                .defaultIfEmpty(false);
                    }

                    return client.get()
                            .uri("/payments/service-health")
                            .retrieve()
                            .bodyToMono(HealthResponse.class)
                            .timeout(Duration.ofMillis(500))
                            .map(resp -> !resp.failing())
                            .onErrorResume(WebClientResponseException.TooManyRequests.class, e -> {
                                String ra = e.getHeaders().getFirst("Retry-After");
                                Duration wait = (ra != null ? Duration.ofSeconds(Long.parseLong(ra)) : Duration.ofSeconds(5));
                                return mongo.save(new HealthCache(processorId, false, Instant.now().plus(wait)))
                                        .thenReturn(false);
                            })
                            .onErrorReturn(false)
                            .flatMap(healthy ->
                                    mongo.save(new HealthCache(processorId, healthy, Instant.now().plus(CACHE_TTL)))
                                            .thenReturn(healthy)
                            );
                });
    }
}