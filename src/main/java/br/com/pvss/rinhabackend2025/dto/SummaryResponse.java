package br.com.pvss.rinhabackend2025.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record SummaryResponse(
        @JsonProperty("default") PaymentsSummaryResponse DEFAULT,
        @JsonProperty("fallback") PaymentsSummaryResponse FALLBACK
) {
}