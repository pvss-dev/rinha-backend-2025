package br.com.pvss.rinhabackend2025.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record PaymentRequestDto(
        UUID correlationId,
        BigDecimal amount
) {
}