package br.com.pvssdev.domain.model;

import br.com.pvssdev.application.dto.PaymentRequestDto;
import io.quarkus.hibernate.reactive.panache.PanacheEntity;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "payments")

public class Payment extends PanacheEntity {

    @Column(unique = true, nullable = false)
    public UUID correlationId;

    @Column(nullable = false)
    public BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    public ProcessorType processor;

    @Column(nullable = false)
    public Instant createdAt;

    public Payment() {
    }

    public Payment(PaymentRequestDto request, ProcessorType processor) {
        this.correlationId = request.correlationId();
        this.amount = request.amount();
        this.processor = processor;
        this.createdAt = Instant.now();
    }
}