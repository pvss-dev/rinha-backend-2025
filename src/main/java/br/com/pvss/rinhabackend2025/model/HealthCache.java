package br.com.pvss.rinhabackend2025.model;

import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document("health_cache")
public record HealthCache(
        String id,
        boolean healthy,
        Instant validUntil
) {
}