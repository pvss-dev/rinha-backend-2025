package br.com.pvss.rinhabackend2025.dto;

import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

public record PaymentRequestDto(
        @NotNull
        UUID correlationId,
        @NotNull
        BigDecimal amount
) {
}