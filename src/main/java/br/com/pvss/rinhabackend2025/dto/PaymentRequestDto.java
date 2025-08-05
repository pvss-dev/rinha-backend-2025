package br.com.pvss.rinhabackend2025.dto;

import org.springframework.aot.hint.annotation.RegisterReflectionForBinding;

import java.math.BigDecimal;

@RegisterReflectionForBinding
public record PaymentRequestDto(
        String correlationId,
        BigDecimal amount,
        String requestedAt
) {
}