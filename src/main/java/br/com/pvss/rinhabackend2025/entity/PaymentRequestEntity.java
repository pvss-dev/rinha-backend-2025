package br.com.pvss.rinhabackend2025.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "payment_requests")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentRequestEntity {

    public enum PaymentStatus {
        PENDING,
        PROCESSING,
        SUCCESS,
        FAILED
    }

    @Id
    @Column(name = "correlation_id", nullable = false, updatable = false)
    private UUID correlationId;

    @Column(nullable = false)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentStatus status;

    @Column(updatable = false)
    private Instant createdAt;

    private Instant updatedAt;

    private String processor;
}