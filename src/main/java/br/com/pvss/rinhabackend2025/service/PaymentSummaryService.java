package br.com.pvss.rinhabackend2025.service;

import br.com.pvss.rinhabackend2025.dto.PaymentProcessor;
import br.com.pvss.rinhabackend2025.dto.PaymentSummaryResponse;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;

import static org.springframework.data.mongodb.core.aggregation.Aggregation.*;

@Service
public class PaymentSummaryService {

    private final MongoTemplate mongoTemplate;

    public PaymentSummaryService(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    public PaymentSummaryResponse summary(Instant from, Instant to) {
        Criteria dateCriteria = Criteria.where("createdAt")
                .gte(from)
                .lte(to);

        Aggregation aggregation = newAggregation(
                match(dateCriteria),
                group("paymentProcessorDefault")
                        .sum("amount").as("totalAmount")
                        .count().as("totalRequests")
        );

        var results = mongoTemplate.aggregate(aggregation, "payments", Document.class).getMappedResults();

        BigDecimal defaultAmount = BigDecimal.ZERO;
        long defaultRequests = 0;
        BigDecimal fallbackAmount = BigDecimal.ZERO;
        long fallbackRequests = 0;

        for (Document doc : results) {
            Boolean isDefault = doc.getBoolean("_id");

            Number amountNumber = doc.get("totalAmount", Number.class);
            BigDecimal amount = amountNumber != null
                    ? new BigDecimal(amountNumber.toString())
                    : BigDecimal.ZERO;

            int count = doc.getInteger("totalRequests", 0);

            if (Boolean.TRUE.equals(isDefault)) {
                defaultAmount = amount;
                defaultRequests = count;
            } else {
                fallbackAmount = amount;
                fallbackRequests = count;
            }
        }

        PaymentProcessor defaultSummary = new PaymentProcessor((int) defaultRequests, defaultAmount);
        PaymentProcessor fallbackSummary = new PaymentProcessor((int) fallbackRequests, fallbackAmount);

        return new PaymentSummaryResponse(defaultSummary, fallbackSummary);
    }
}