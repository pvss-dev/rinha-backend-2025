package br.com.pvss.rinhabackend2025.service;

import br.com.pvss.rinhabackend2025.dto.PaymentsSummaryResponse;
import br.com.pvss.rinhabackend2025.dto.ProcessorPaymentRequest;
import br.com.pvss.rinhabackend2025.dto.ProcessorType;
import br.com.pvss.rinhabackend2025.dto.SummaryResponse;
import br.com.pvss.rinhabackend2025.model.PaymentEvent;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationOperation;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Map;
import java.util.stream.Collectors;


@Service
public class MongoSummaryService {

    private final MongoTemplate mongo;

    public MongoSummaryService(MongoTemplate mongo) {
        this.mongo = mongo;
    }

    public void persistPayment(ProcessorType p, ProcessorPaymentRequest payload) {
        long cents = payload.amount().movePointRight(2).longValueExact();
        PaymentEvent event = new PaymentEvent(payload.correlationId(), cents, p, payload.requestedAt());
        mongo.save(event);
    }

    private PaymentsSummaryResponse toPaymentsSummary(Document doc) {

        long reqs = getNumberAsLong(doc, "totalRequests");
        long amountCents = getNumberAsLong(doc, "totalAmountCents");

        BigDecimal totalAmount = BigDecimal.valueOf(amountCents).movePointLeft(2);

        int totalRequests = Math.toIntExact(reqs);

        return new PaymentsSummaryResponse(totalAmount, totalRequests, null, null);
    }

    private SummaryResponse toResponseWithDefaults(Map<ProcessorType, PaymentsSummaryResponse> map) {
        PaymentsSummaryResponse def = map.getOrDefault(
                ProcessorType.DEFAULT,
                new PaymentsSummaryResponse(BigDecimal.ZERO, 0, null, null)
        );
        PaymentsSummaryResponse fb = map.getOrDefault(
                ProcessorType.FALLBACK,
                new PaymentsSummaryResponse(BigDecimal.ZERO, 0, null, null)
        );
        return new SummaryResponse(def, fb);
    }

    private static long getNumberAsLong(Document doc, String key) {
        Object v = doc.get(key);
        if (v instanceof Number n) return n.longValue();
        throw new IllegalStateException("Campo '" + key + "' não é numérico: " + v);
    }

    public SummaryResponse summary(Instant from, Instant to) {
        var ops = new ArrayList<AggregationOperation>();
        if (from != null && to != null) {
            ops.add(Aggregation.match(Criteria.where("requestedAt").gte(from).lte(to)));
        }
        ops.add(Aggregation.group("processor")
                .count().as("totalRequests")
                .sum("amountCents").as("totalAmountCents"));

        return toResponseWithDefaults(
                mongo.aggregate(Aggregation.newAggregation(ops), "payment_events", Document.class)
                        .getMappedResults()
                        .stream()
                        .collect(Collectors.toMap(doc -> ProcessorType.valueOf(doc.getString("_id")), this::toPaymentsSummary))
        );
    }
}