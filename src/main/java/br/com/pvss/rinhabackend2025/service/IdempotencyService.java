package br.com.pvss.rinhabackend2025.service;

import java.time.Instant;
import java.util.UUID;

import br.com.pvss.rinhabackend2025.model.ReceivedRequest;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
public class IdempotencyService {

    private final ReactiveMongoTemplate template;

    public IdempotencyService(ReactiveMongoTemplate template) {
        this.template = template;
    }

    public Mono<Boolean> acquire(UUID correlationId, Instant requestedAt) {
        Query q = Query.query(Criteria.where("correlationId").is(correlationId));

        Update u = new Update()
                .setOnInsert("correlationId", correlationId)
                .setOnInsert("firstSeenAt", requestedAt);

        return template.findAndModify(
                        q, u,
                        FindAndModifyOptions.options().upsert(true).returnNew(false),
                        ReceivedRequest.class)
                .map(existing -> false)
                .defaultIfEmpty(true);
    }
}