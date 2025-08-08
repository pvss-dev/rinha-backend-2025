package br.com.pvss.rinhabackend2025.model;

import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.UUID;

@Document("received_requests")
@CompoundIndex(name = "uk_corr", def = "{'correlationId':1}", unique = true)
public record ReceivedRequest(
        UUID correlationId,
        Instant firstSeenAt
) {
}