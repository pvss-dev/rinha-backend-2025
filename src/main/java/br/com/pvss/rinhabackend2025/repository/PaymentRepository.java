package br.com.pvss.rinhabackend2025.repository;

import br.com.pvss.rinhabackend2025.model.PaymentModel;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface PaymentRepository extends MongoRepository<PaymentModel, UUID> {
    List<PaymentModel> findByRequestedAtBetween(Instant from, Instant to);
}