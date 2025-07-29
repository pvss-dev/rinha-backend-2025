package br.com.pvssdev.infrastructure.client.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record ProcessorRequest(
        UUID correlationId,
        BigDecimal amount,
        Instant requestedAt
) {
}