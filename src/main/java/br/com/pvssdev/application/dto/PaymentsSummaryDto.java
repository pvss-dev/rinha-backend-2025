package br.com.pvssdev.application.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record PaymentsSummaryDto(
        @JsonProperty("default")
        ProcessorDetailDto defaultProcessor,

        @JsonProperty("fallback")
        ProcessorDetailDto fallbackProcessor
) {
}
