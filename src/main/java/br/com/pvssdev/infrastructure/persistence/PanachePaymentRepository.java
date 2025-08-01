package br.com.pvssdev.infrastructure.persistence;

import br.com.pvssdev.domain.model.Payment;
import br.com.pvssdev.domain.model.PaymentStatus;
import br.com.pvssdev.domain.model.ProcessorType;
import br.com.pvssdev.domain.repository.PaymentRepository;
import io.quarkus.hibernate.reactive.panache.PanacheRepositoryBase;
import io.quarkus.panache.common.Parameters;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;

import java.time.Instant;
import java.util.List;

@ApplicationScoped
public class PanachePaymentRepository implements PaymentRepository, PanacheRepositoryBase<Payment, Long> {

    @Override
    public Uni<Void> save(Payment payment) {
        return persist(payment).replaceWithVoid();
    }

    public Uni<List<Payment>> findPendingPayments(int limit) {
        return find("status", PaymentStatus.PENDING).page(0, limit).list();
    }

    public Uni<Integer> updatePaymentStatus(Long id, ProcessorType processor, PaymentStatus status) {
        return update("""
                            UPDATE Payment
                            SET status = :status, processor = :processor, updatedAt = :now
                            WHERE id = :id AND status = :pendingStatus
                        """,
                Parameters.with("status", status)
                        .and("processor", processor)
                        .and("now", Instant.now())
                        .and("id", id)
                        .and("pendingStatus", PaymentStatus.PENDING)
        );
    }

    public Uni<Integer> updatePaymentAsFailed(Long id) {
        return update("""
                            UPDATE Payment SET status = :failedStatus, updatedAt = :now
                            WHERE id = :id AND status = :pendingStatus
                        """,
                Parameters.with("failedStatus", PaymentStatus.FAILED)
                        .and("now", Instant.now())
                        .and("id", id)
                        .and("pendingStatus", PaymentStatus.PENDING)
        );
    }

    @Override
    public Uni<List<SummaryQueryDto>> getSummary(Instant from, Instant to) {
        StringBuilder queryBuilder = new StringBuilder("""
                    SELECT p.processor as processor, COUNT(p.id) as totalRequests, SUM(p.amount) as totalAmount
                    FROM Payment p
                    WHERE p.processor IS NOT NULL
                """);

        Parameters params = new Parameters();

        if (from != null) {
            queryBuilder.append(" AND p.createdAt >= :from");
            params.and("from", from);
        }
        if (to != null) {
            queryBuilder.append(" AND p.createdAt <= :to");
            params.and("to", to);
        }

        queryBuilder.append(" GROUP BY p.processor");

        return find(queryBuilder.toString(), params)
                .project(SummaryQueryDto.class)
                .list();
    }
}