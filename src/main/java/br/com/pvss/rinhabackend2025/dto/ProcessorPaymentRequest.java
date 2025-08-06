package br.com.pvss.rinhabackend2025.dto;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record ProcessorPaymentRequest(
        UUID correlationId,
        BigDecimal amount,
        @JsonFormat(shape = JsonFormat.Shape.STRING, timezone = "UTC")
        Instant requestedAt
) {
}