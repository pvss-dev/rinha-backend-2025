package br.com.pvss.rinhabackend2025.dto;

public record HealthStatus(boolean failing, int minResponseTime) {
    public static final HealthStatus UNHEALTHY = new HealthStatus(true, Integer.MAX_VALUE);
}
