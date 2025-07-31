package br.com.pvssdev.domain.repository;

import br.com.pvssdev.domain.model.Payment;
import br.com.pvssdev.infrastructure.persistence.SummaryQueryDto;
import io.smallrye.mutiny.Uni;

import java.time.Instant;
import java.util.List;

public interface PaymentRepository {

    Uni<Void> save(Payment payment);

    Uni<List<SummaryQueryDto>> getSummary(Instant from, Instant to);
}