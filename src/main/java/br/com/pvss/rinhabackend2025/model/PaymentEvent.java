package br.com.pvss.rinhabackend2025.model;

import br.com.pvss.rinhabackend2025.dto.ProcessorType;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.UUID;

@Document("payment_events")
@CompoundIndexes({
        @CompoundIndex(name = "idx_proc_time", def = "{'processor':1,'requestedAt':1}"),
        @CompoundIndex(name = "uk_corr", def = "{'correlationId':1}", unique = true)
})
public record PaymentEvent(
        UUID correlationId,
        long amountCents,
        ProcessorType processor,
        Instant requestedAt
) {
}