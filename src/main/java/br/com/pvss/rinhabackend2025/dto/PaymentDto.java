package br.com.pvss.rinhabackend2025.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.aot.hint.annotation.RegisterReflectionForBinding;

import java.math.BigDecimal;

@RegisterReflectionForBinding
public record PaymentDto(
        @JsonProperty("correlationId")
        String correlationId,
        @JsonProperty("amount")
        BigDecimal amount,
        @JsonProperty("requestedAt")
        String requestedAt
) {
}
