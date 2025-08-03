package br.com.pvss.rinhabackend2025.service;

import br.com.pvss.rinhabackend2025.entity.PaymentRequestEntity;
import br.com.pvss.rinhabackend2025.repository.PaymentRequestRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentTransactionHandler {

    private final PaymentRequestRepository repository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void handleSuccess(PaymentRequestEntity request, String processor) {
        request.setStatus(PaymentRequestEntity.PaymentStatus.SUCCESS);
        request.setProcessor(processor);
        request.setUpdatedAt(Instant.now());
        repository.save(request);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void handleError(PaymentRequestEntity request) {
        log.warn("Returning payment {} to PENDING due to processing error.", request.getCorrelationId());
        request.setStatus(PaymentRequestEntity.PaymentStatus.PENDING);
        request.setUpdatedAt(Instant.now());
        repository.save(request);
    }
}