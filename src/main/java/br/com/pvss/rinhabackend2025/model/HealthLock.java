package br.com.pvss.rinhabackend2025.model;

import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document("health_lock")
public record HealthLock(
        String id,
        String owner,
        Instant expiresAt
) {
}