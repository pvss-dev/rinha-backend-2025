package br.com.pvssdev.infrastructure.client.dto;

public record HealthStatus(
        boolean failing,
        int minResponseTime
) {
}