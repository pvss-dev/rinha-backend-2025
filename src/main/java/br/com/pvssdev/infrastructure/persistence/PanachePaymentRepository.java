package br.com.pvssdev.infrastructure.persistence;

import br.com.pvssdev.domain.model.Payment;
import br.com.pvssdev.domain.model.PaymentStatus;
import br.com.pvssdev.domain.model.ProcessorType;
import br.com.pvssdev.domain.repository.PaymentRepository;
import io.quarkus.hibernate.reactive.panache.PanacheRepositoryBase;
import io.quarkus.panache.common.Parameters;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.hibernate.reactive.mutiny.Mutiny;

import java.time.Instant;
import java.util.List;

@ApplicationScoped
public class PanachePaymentRepository implements PaymentRepository, PanacheRepositoryBase<Payment, Long> {

    @Inject
    Mutiny.SessionFactory sf;

    @Override
    public Uni<Void> save(Payment payment) {
        return persist(payment).replaceWithVoid();
    }

    public Uni<List<Payment>> findAndLockPendingPayments(int limit) {
        return sf.withSession(session ->
                session.createNativeQuery(
                                """
                                        SELECT * FROM payments WHERE status = 'PENDING' ORDER BY "created_at" ASC LIMIT :limit FOR UPDATE SKIP LOCKED
                                        """,
                                Payment.class)
                        .setParameter("limit", limit)
                        .getResultList()
        );
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
        String query = """
                    SELECT p.processor as processor, COUNT(p.id) as totalRequests, SUM(p.amount) as totalAmount
                    FROM Payment p
                    WHERE p.createdAt >= :from AND p.createdAt <= :to AND p.processor IS NOT NULL
                    GROUP BY p.processor
                """;
        return find(query, Parameters.with("from", from).and("to", to))
                .project(SummaryQueryDto.class)
                .list();
    }
}