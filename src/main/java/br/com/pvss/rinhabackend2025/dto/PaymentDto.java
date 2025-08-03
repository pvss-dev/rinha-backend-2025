package br.com.pvss.rinhabackend2025.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.math.BigDecimal;

public record PaymentDto(
        @NotNull
        @Pattern(
                regexp = "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$",
                message = "correlationId must be a valid UUID"
        )
        String correlationId,
        @NotNull
        @DecimalMin(
                value = "0.01",
                message = "amount must be greater than 0"
        )
        BigDecimal amount
) {
}