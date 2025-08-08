package br.com.pvss.rinhabackend2025.service;

import br.com.pvss.rinhabackend2025.dto.HealthResponse;
import br.com.pvss.rinhabackend2025.dto.ProcessorType;
import br.com.pvss.rinhabackend2025.model.HealthCache;
import br.com.pvss.rinhabackend2025.model.HealthLock;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Service
public class HealthCheckService {

    private static final Duration LOCK_TTL = Duration.ofSeconds(5);
    private static final Duration CACHE_TTL = Duration.ofSeconds(6);
    private static final String DEFAULT_ID = "default";

    private final ReactiveMongoTemplate mongo;
    private final WebClient ppDefault;
    private final String hostId = System.getenv().getOrDefault("HOSTNAME", UUID.randomUUID().toString());

    public HealthCheckService(ReactiveMongoTemplate mongo,
                              @Qualifier("defaultProcessorClient") WebClient ppDefault) {
        this.mongo = mongo;
        this.ppDefault = ppDefault;
    }

    public Mono<ProcessorType> getAvailableProcessor() {
        return isDefaultHealthy()
                .map(healthy -> healthy ? ProcessorType.DEFAULT : ProcessorType.FALLBACK);
    }

    private Mono<Boolean> isDefaultHealthy() {
        return mongo.findById(DEFAULT_ID, HealthCache.class)
                .filter(c -> c.validUntil() != null && c.validUntil().isAfter(Instant.now()))
                .map(HealthCache::healthy)
                .switchIfEmpty(refreshDefaultHealth());
    }

    private Mono<Boolean> refreshDefaultHealth() {
        Instant now = Instant.now();
        Instant lease = now.plus(LOCK_TTL);

        Query q = new Query(
                Criteria.where("_id").is(DEFAULT_ID)
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
                                .then(mongo.findById(DEFAULT_ID, HealthCache.class))
                                .map(c -> c != null && c.healthy())
                                .defaultIfEmpty(false);
                    }

                    return ppDefault.get()
                            .uri("/health")
                            .retrieve()
                            .bodyToMono(HealthResponse.class)
                            .timeout(Duration.ofMillis(500))
                            .map(resp -> !resp.failing())
                            .onErrorReturn(false)
                            .flatMap(healthy ->
                                    mongo.save(new HealthCache(DEFAULT_ID, healthy, Instant.now().plus(CACHE_TTL)))
                                            .thenReturn(healthy)
                            );
                });
    }
}