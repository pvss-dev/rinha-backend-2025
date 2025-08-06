package br.com.pvss.rinhabackend2025.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PaymentDto(
        UUID correlationId,
        BigDecimal amount,
        Instant requestedAt
) {
}