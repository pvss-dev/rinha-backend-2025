package br.com.pvss.rinhabackend2025.service;

import br.com.pvss.rinhabackend2025.dto.PaymentDto;
import br.com.pvss.rinhabackend2025.entity.PaymentRequestEntity;
import br.com.pvss.rinhabackend2025.repository.PaymentRequestRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentRequestRepository paymentRequestRepository;
    private final PaymentWorker paymentWorker;

    public void acceptPayment(@Valid PaymentDto dto) {
        PaymentRequestEntity entity = PaymentRequestEntity.builder()
                .correlationId(UUID.fromString(dto.correlationId()))
                .amount(dto.amount())
                .status(PaymentRequestEntity.PaymentStatus.PENDING)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        paymentRequestRepository.save(entity);

        paymentWorker.processPendingPayments();
    }
}