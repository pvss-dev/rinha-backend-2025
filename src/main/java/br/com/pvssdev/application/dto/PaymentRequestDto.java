package br.com.pvssdev.application.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record PaymentRequestDto(
        UUID correlationId,
        BigDecimal amount
) {
}