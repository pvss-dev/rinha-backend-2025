package br.com.pvss.rinhabackend2025.repository;

import br.com.pvss.rinhabackend2025.entity.PaymentRequestEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Repository
public interface PaymentRequestRepository extends JpaRepository<PaymentRequestEntity, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<PaymentRequestEntity> findTop10ByStatusOrderByCreatedAtAsc(PaymentRequestEntity.PaymentStatus status);

    @Query("""
                SELECT
                    p.processor AS processor,
                    SUM(p.amount) AS totalAmount,
                    COUNT(p) AS totalRequests
                FROM
                    PaymentRequestEntity p
                WHERE
                    p.status = :status AND
                    (:from IS NULL OR p.updatedAt >= :from) AND
                    (:to IS NULL OR p.updatedAt <= :to)
                GROUP BY
                    p.processor
            """)
    List<Map<String, Object>> getSummary(
            @Param("from") Instant from,
            @Param("to") Instant to,
            @Param("status") PaymentRequestEntity.PaymentStatus status
    );
}