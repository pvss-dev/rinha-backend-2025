package br.com.pvss.rinhabackend2025.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Document("payments")
@CompoundIndex(def = "{'requestedAt': 1, 'processor': 1}", name = "requested_at_processor_idx")
public class PaymentModel {

    @Id
    private UUID correlationId;

    private BigDecimal amount;

    private Instant requestedAt;

    private String processor;

    public PaymentModel() {
    }

    public PaymentModel(UUID correlationId, BigDecimal amount, Instant requestedAt, String processor) {
        this.correlationId = correlationId;
        this.amount = amount;
        this.requestedAt = requestedAt;
        this.processor = processor;
    }

    public UUID getCorrelationId() {
        return correlationId;
    }

    public void setCorrelationId(UUID correlationId) {
        this.correlationId = correlationId;
    }


    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public Instant getRequestedAt() {
        return requestedAt;
    }

    public void setRequestedAt(Instant requestedAt) {
        this.requestedAt = requestedAt;
    }

    public String getProcessor() {
        return processor;
    }

    public void setProcessor(String processor) {
        this.processor = processor;
    }
}