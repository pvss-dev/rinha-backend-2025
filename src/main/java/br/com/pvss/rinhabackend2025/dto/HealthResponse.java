package br.com.pvss.rinhabackend2025.dto;

public record HealthResponse(
        boolean failing,
        int minResponseTime
) {
}