package br.com.pvss.rinhabackend2025.service;

import br.com.pvss.rinhabackend2025.config.PaymentProcessorClient;
import br.com.pvss.rinhabackend2025.dto.PaymentProcessorRequest;
import br.com.pvss.rinhabackend2025.dto.ProcessorPaymentRequest;
import br.com.pvss.rinhabackend2025.model.PaymentDocument;
import br.com.pvss.rinhabackend2025.repository.PaymentRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
public class PaymentProcessorService {

    private static final Logger LOGGER = LoggerFactory.getLogger(PaymentProcessorService.class);
    private final PaymentRepository paymentRepository;
    private final PaymentProcessorClient paymentProcessorDefault;
    private final PaymentProcessorClient paymentProcessorFallback;
    private final ObjectMapper objectMapper;

    public PaymentProcessorService(
            PaymentRepository paymentRepository,
            @Qualifier("paymentProcessorDefaultClient") PaymentProcessorClient paymentProcessorDefault,
            @Qualifier("paymentProcessorFallbackClient") PaymentProcessorClient paymentProcessorFallback,
            ObjectMapper objectMapper) {
        this.paymentRepository = paymentRepository;
        this.paymentProcessorDefault = paymentProcessorDefault;
        this.paymentProcessorFallback = paymentProcessorFallback;
        this.objectMapper = objectMapper;
    }

    @Async
    public void processAndSavePayment(PaymentProcessorRequest paymentRequest) {
        ProcessorPaymentRequest processorRequest = new ProcessorPaymentRequest(
                paymentRequest.correlationId(),
                paymentRequest.amount(),
                Instant.now()
        );

        String paymentJson;
        try {
            paymentJson = objectMapper.writeValueAsString(processorRequest);
        } catch (JsonProcessingException e) {
            LOGGER.error("Failed to serialize payment request for correlationId: {}", processorRequest.correlationId(), e);
            return;
        }

        if (paymentProcessorDefault.processPayment(paymentJson)) {
            savePayment(processorRequest, true);
        } else if (paymentProcessorFallback.processPayment(paymentJson)) {
            savePayment(processorRequest, false);
        } else {
            LOGGER.warn("Payment failed for both processors. CorrelationId: {}", processorRequest.correlationId());
        }
    }

    private void savePayment(ProcessorPaymentRequest request, boolean isDefault) {
        if (paymentRepository.existsByCorrelationId(request.correlationId().toString())) {
            LOGGER.warn("Attempted to save a duplicate payment with correlationId: {}", request.correlationId());
            return;
        }

        PaymentDocument doc = new PaymentDocument();
        doc.setCorrelationId(request.correlationId().toString());
        doc.setAmount(request.amount());
        doc.setPaymentProcessorDefault(isDefault);
        doc.setCreatedAt(request.requestedAt());
        paymentRepository.save(doc);
    }
}