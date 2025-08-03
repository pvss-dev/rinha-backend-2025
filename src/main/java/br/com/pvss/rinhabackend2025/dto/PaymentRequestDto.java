package br.com.pvss.rinhabackend2025.dto;

import java.math.BigDecimal;

public record PaymentRequestDto(
        String correlationId,
        BigDecimal amount,
        String requestedAt
) {
}