package br.com.pvss.rinhabackend2025.service;

import br.com.pvss.rinhabackend2025.dto.PaymentsSummaryResponse;
import br.com.pvss.rinhabackend2025.dto.ProcessorPaymentRequest;
import br.com.pvss.rinhabackend2025.dto.ProcessorType;
import br.com.pvss.rinhabackend2025.dto.SummaryResponse;
import br.com.pvss.rinhabackend2025.model.PaymentEvent;
import br.com.pvss.rinhabackend2025.model.SummaryData;
import com.mongodb.DuplicateKeyException;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationOperation;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
public class MongoSummaryService {

    private static final Logger logger = LoggerFactory.getLogger(MongoSummaryService.class);
    private final MongoTemplate mongo;
    private final Map<ProcessorType, PaymentsSummaryResponse> summaryCache = new ConcurrentHashMap<>();

    public MongoSummaryService(MongoTemplate mongo) {
        this.mongo = mongo;
        initializeSummaryCache();
    }

    public void persistPayment(ProcessorType p, ProcessorPaymentRequest payload) {
        long cents = payload.amount().movePointRight(2).longValueExact();
        PaymentEvent event = new PaymentEvent(payload.correlationId(), cents, p, payload.requestedAt());

        try {
            mongo.insert(event);
            updatePersistedSummaryData(p, cents);
            updateSummaryCache(p, cents);
        } catch (DuplicateKeyException e) {
            logger.warn("Duplicate payment event detected for correlationId: {}", payload.correlationId(), e);
        } catch (Exception e) {
            logger.error("Erro ao persistir pagamento ou atualizar resumo", e);
        }
    }

    public SummaryResponse summary(Instant from, Instant to) {
        if (from != null && to != null) {
            var ops = new ArrayList<AggregationOperation>();
            ops.add(Aggregation.match(Criteria.where("requestedAt").gte(from).lte(to)));
            ops.add(Aggregation.group("processor")
                    .count().as("totalRequests")
                    .sum("amountCents").as("totalAmountCents"));

            return toResponseWithDefaults(
                    mongo.aggregate(Aggregation.newAggregation(ops), "payment_events", Document.class)
                            .getMappedResults()
                            .stream()
                            .collect(Collectors.toMap(doc -> ProcessorType.valueOf(doc.getString("_id")), this::toPaymentsSummary))
            );
        } else {
            PaymentsSummaryResponse def = summaryCache.getOrDefault(ProcessorType.DEFAULT, new PaymentsSummaryResponse(BigDecimal.ZERO, 0));
            PaymentsSummaryResponse fb = summaryCache.getOrDefault(ProcessorType.FALLBACK, new PaymentsSummaryResponse(BigDecimal.ZERO, 0));
            return new SummaryResponse(def, fb);
        }
    }

    private PaymentsSummaryResponse toPaymentsSummary(Document doc) {
        long reqs = getNumberAsLong(doc, "totalRequests");
        long amountCents = getNumberAsLong(doc, "totalAmountCents");
        BigDecimal totalAmount = BigDecimal.valueOf(amountCents).movePointLeft(2);
        int totalRequests = Math.toIntExact(reqs);
        return new PaymentsSummaryResponse(totalAmount, totalRequests);
    }

    private SummaryResponse toResponseWithDefaults(Map<ProcessorType, PaymentsSummaryResponse> map) {
        PaymentsSummaryResponse def = map.getOrDefault(ProcessorType.DEFAULT, new PaymentsSummaryResponse(BigDecimal.ZERO, 0));
        PaymentsSummaryResponse fb = map.getOrDefault(ProcessorType.FALLBACK, new PaymentsSummaryResponse(BigDecimal.ZERO, 0));
        return new SummaryResponse(def, fb);
    }

    private static long getNumberAsLong(Document doc, String key) {
        Object v = doc.get(key);
        if (v instanceof Number n) return n.longValue();
        throw new IllegalStateException("Campo '" + key + "' não é numérico: " + v);
    }

    private void initializeSummaryCache() {
        for (ProcessorType type : ProcessorType.values()) {
            SummaryData data = mongo.findById(type, SummaryData.class);
            if (data != null) {
                BigDecimal totalAmount = BigDecimal.valueOf(data.getTotalAmountCents()).movePointLeft(2);
                summaryCache.put(type, new PaymentsSummaryResponse(totalAmount, (int) data.getTotalRequests()));
            } else {
                summaryCache.put(type, new PaymentsSummaryResponse(BigDecimal.ZERO, 0));
            }
        }
    }

    private void updateSummaryCache(ProcessorType p, long amountCents) {
        summaryCache.compute(p, (key, value) -> {
            if (value == null) {
                return new PaymentsSummaryResponse(BigDecimal.valueOf(amountCents).movePointLeft(2), 1);
            }
            long newRequests = value.totalRequests() + 1;
            BigDecimal newAmount = value.totalAmount().add(BigDecimal.valueOf(amountCents).movePointLeft(2));
            return new PaymentsSummaryResponse(newAmount, (int) newRequests);
        });
    }

    private void updatePersistedSummaryData(ProcessorType p, long amountCents) {
        Query query = Query.query(Criteria.where("_id").is(p));
        Update update = new Update()
                .inc("totalRequests", 1)
                .inc("totalAmountCents", amountCents);
        mongo.upsert(query, update, SummaryData.class);
    }
}