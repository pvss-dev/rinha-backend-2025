package br.com.pvss.rinhabackend2025.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PaymentProcessorRequest(
        @NotNull
        UUID correlationId,
        @NotNull
        BigDecimal amount,
        @JsonFormat(shape = JsonFormat.Shape.STRING, timezone = "UTC")
        Instant requestedAt
) {
}