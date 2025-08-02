package br.com.pvss.rinhabackend2025.repository;

import br.com.pvss.rinhabackend2025.entity.PaymentEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface PaymentRepository extends JpaRepository<PaymentEntity, UUID> {
}