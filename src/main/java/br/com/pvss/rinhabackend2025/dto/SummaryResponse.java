package br.com.pvss.rinhabackend2025.dto;

public record SummaryResponse(
        SummaryItem _default,
        SummaryItem fallback
) {
}