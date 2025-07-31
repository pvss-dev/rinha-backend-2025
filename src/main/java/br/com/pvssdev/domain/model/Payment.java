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
    public ProcessorType processor;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    public PaymentStatus status;

    @Column(nullable = false)
    public Instant createdAt;

    @Column
    public Instant updatedAt;

    public Payment() {
    }

    public Payment(PaymentRequestDto request) {
        this.correlationId = request.correlationId();
        this.amount = request.amount();
        this.status = PaymentStatus.PENDING;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }
}