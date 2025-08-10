package br.com.pvss.rinhabackend2025.service;

import br.com.pvss.rinhabackend2025.dto.PaymentsSummaryResponse;
import br.com.pvss.rinhabackend2025.dto.ProcessorType;
import br.com.pvss.rinhabackend2025.dto.SummaryResponse;
import br.com.pvss.rinhabackend2025.model.PaymentEvent;
import org.bson.Document;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationOperation;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;

@Service
public class MongoSummaryService {

    private final ReactiveMongoTemplate mongo;
    private final BigDecimal defaultFeePerTx;
    private final BigDecimal fallbackFeePerTx;

    public MongoSummaryService(
            ReactiveMongoTemplate mongo,
            @Value("${payment.processor.default.fee}")
            BigDecimal defaultFeePerTx,
            @Value("${payment.processor.fallback.fee}")
            BigDecimal fallbackFeePerTx
    ) {
        this.mongo = mongo;
        this.defaultFeePerTx = defaultFeePerTx;
        this.fallbackFeePerTx = fallbackFeePerTx;
    }

    public Mono<Void> persistPaymentSummary(ProcessorType p, BigDecimal amount, UUID corr, Instant requestedAt) {
        long cents = amount.movePointRight(2).longValueExact();
        Query q = new Query(Criteria.where("processor").is(p).and("correlationId").is(corr));
        Update u = new Update()
                .setOnInsert("processor", p)
                .setOnInsert("correlationId", corr)
                .setOnInsert("requestedAt", requestedAt)
                .setOnInsert("amountCents", cents);

        return mongo.upsert(q, u, PaymentEvent.class)
                .then();
    }

    private PaymentsSummaryResponse toPaymentsSummary(Document doc) {
        ProcessorType processor = ProcessorType.valueOf(doc.getString("_id"));

        long reqs = getNumberAsLong(doc, "totalRequests");
        long amountCents = getNumberAsLong(doc, "totalAmountCents");

        BigDecimal totalAmount = BigDecimal.valueOf(amountCents).movePointLeft(2);
        BigDecimal feePerTx = (processor == ProcessorType.DEFAULT ? defaultFeePerTx : fallbackFeePerTx);
        BigDecimal totalFee = totalAmount.multiply(feePerTx).setScale(2, RoundingMode.HALF_UP);

        int totalRequests = Math.toIntExact(reqs);

        return new PaymentsSummaryResponse(totalAmount, totalRequests, totalFee, feePerTx);
    }

    private SummaryResponse toResponseWithDefaults(Map<ProcessorType, PaymentsSummaryResponse> map) {
        PaymentsSummaryResponse def = map.getOrDefault(
                ProcessorType.DEFAULT,
                new PaymentsSummaryResponse(BigDecimal.ZERO, 0,
                        BigDecimal.ZERO, defaultFeePerTx)
        );
        PaymentsSummaryResponse fb = map.getOrDefault(
                ProcessorType.FALLBACK,
                new PaymentsSummaryResponse(BigDecimal.ZERO, 0,
                        BigDecimal.ZERO, fallbackFeePerTx)
        );
        return new SummaryResponse(def, fb);
    }

    private static long getNumberAsLong(Document doc, String key) {
        Object v = doc.get(key);
        if (v instanceof Number n) return n.longValue();
        throw new IllegalStateException("Campo '" + key + "' não é numérico: " + v);
    }

    public Mono<SummaryResponse> summaryNullable(Instant from, Instant to) {
        if (from == null || to == null) {
            return aggregate(null, null);
        }
        return aggregate(from, to);
    }

    private Mono<SummaryResponse> aggregate(Instant from, Instant to) {
        var ops = new ArrayList<AggregationOperation>();
        if (from != null && to != null) {
            ops.add(Aggregation.match(Criteria.where("requestedAt").gte(from).lte(to)));
        }
        ops.add(Aggregation.group("processor")
                .count().as("totalRequests")
                .sum("amountCents").as("totalAmountCents"));

        return mongo.aggregate(Aggregation.newAggregation(ops), "payment_events", Document.class)
                .collectMap(doc -> ProcessorType.valueOf(doc.getString("_id")), this::toPaymentsSummary)
                .map(this::toResponseWithDefaults);
    }
}