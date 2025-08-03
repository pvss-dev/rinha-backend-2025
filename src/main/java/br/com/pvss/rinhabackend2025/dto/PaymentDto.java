package br.com.pvss.rinhabackend2025.dto;

import java.math.BigDecimal;

public record PaymentDto(
        String correlationId,
        BigDecimal amount
) {
}