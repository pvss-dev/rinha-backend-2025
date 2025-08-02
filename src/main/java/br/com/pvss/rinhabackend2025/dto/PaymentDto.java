package br.com.pvss.rinhabackend2025.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.math.BigDecimal;

public record PaymentDto(
        @NotNull
        @Pattern(regexp = "[0-9a-fA-F\\-]{36}")
        String correlationId,
        @NotNull
        @DecimalMin("0.01")
        BigDecimal amount
) {
}