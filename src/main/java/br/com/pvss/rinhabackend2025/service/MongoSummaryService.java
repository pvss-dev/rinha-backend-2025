package br.com.pvss.rinhabackend2025.service;

import br.com.pvss.rinhabackend2025.dto.ProcessorType;
import br.com.pvss.rinhabackend2025.dto.SummaryItem;
import br.com.pvss.rinhabackend2025.dto.SummaryResponse;
import br.com.pvss.rinhabackend2025.model.PaymentEvent;
import org.bson.Document;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.GroupOperation;
import org.springframework.data.mongodb.core.aggregation.MatchOperation;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Service
public class MongoSummaryService {

    private final ReactiveMongoTemplate mongo;

    public MongoSummaryService(ReactiveMongoTemplate mongo) {
        this.mongo = mongo;
    }

    public Mono<Void> persistPaymentSummary(ProcessorType p, BigDecimal amount, UUID corr, Instant requestedAt) {
        long cents = amount.movePointRight(2).longValueExact();

        Query q = new Query(Criteria.where("processor").is(p).and("correlationId").is(corr));
        Update u = new Update()
                .setOnInsert("processor", p)
                .setOnInsert("correlationId", corr)
                .setOnInsert("requestedAt", requestedAt)
                .setOnInsert("amountCents", cents);

        return mongo.upsert(q, u, PaymentEvent.class).then();
    }

    public Mono<SummaryResponse> summary(Instant from, Instant to) {
        MatchOperation match = Aggregation.match(Criteria.where("requestedAt").gte(from).lte(to));
        GroupOperation group = Aggregation.group("processor")
                .count().as("totalRequests")
                .sum("amountCents").as("totalAmountCents");

        return mongo.aggregate(Aggregation.newAggregation(match, group), "payment_events", Document.class)
                .collectMap(doc -> ProcessorType.valueOf(doc.getString("_id")),
                        doc -> new SummaryItem(
                                doc.getLong("totalRequests"),
                                new BigDecimal(doc.getLong("totalAmountCents")).movePointLeft(2)))
                .map(map -> new SummaryResponse(
                        map.getOrDefault(ProcessorType.DEFAULT, new SummaryItem(0, BigDecimal.ZERO)),
                        map.getOrDefault(ProcessorType.FALLBACK, new SummaryItem(0, BigDecimal.ZERO))
                ));
    }
}