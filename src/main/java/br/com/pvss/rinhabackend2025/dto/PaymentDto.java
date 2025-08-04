package br.com.pvss.rinhabackend2025.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record PaymentDto(
        String correlationId,
        BigDecimal amount,
        Instant requestedAt
) {
}
