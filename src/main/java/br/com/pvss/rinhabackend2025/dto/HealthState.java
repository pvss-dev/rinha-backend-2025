package br.com.pvss.rinhabackend2025.dto;

public record HealthState(boolean healthy, int minResponseTime, long timestamp) {
}