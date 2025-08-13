package br.com.pvss.rinhabackend2025.repository;

import br.com.pvss.rinhabackend2025.model.PaymentDocument;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface PaymentRepository extends MongoRepository<PaymentDocument, String> {
    boolean existsByCorrelationId(String correlationId);
}
