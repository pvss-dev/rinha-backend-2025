package br.com.pvssdev.infrastructure.persistence;

import br.com.pvssdev.domain.model.Payment;
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

    @Override
    public Uni<List<SummaryQueryDto>> getSummary(Instant from, Instant to) {
        String query = """
                    SELECT p.processor as processor, COUNT(p.id) as totalRequests, SUM(p.amount) as totalAmount
                    FROM Payment p
                    WHERE p.createdAt >= :from AND p.createdAt <= :to
                    GROUP BY p.processor
                """;
        return find(query, Parameters.with("from", from).and("to", to))
                .project(SummaryQueryDto.class)
                .list();
    }
}