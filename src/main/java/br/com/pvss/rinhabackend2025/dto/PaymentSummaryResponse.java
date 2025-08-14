package br.com.pvss.rinhabackend2025.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record PaymentSummaryResponse(
        @JsonProperty("default")
        PaymentProcessor defaultValue,
        PaymentProcessor fallback
) {
}