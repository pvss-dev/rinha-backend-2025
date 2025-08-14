package br.com.pvss.rinhabackend2025.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;

public record PaymentSummaryResponse(

        @JsonProperty("default")
        SummaryDetails defaultSummary,

        @JsonProperty("fallback")
        SummaryDetails fallbackSummary
) {

    public record SummaryDetails(
            long totalRequests,
            BigDecimal totalAmount
    ) {
    }
}