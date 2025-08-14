package br.com.pvss.rinhabackend2025.model;

import br.com.pvss.rinhabackend2025.dto.PaymentProcessorRequest;

public record CompletePaymentProcessor(
        String paymentJson,
        PaymentProcessorRequest paymentProcessorRequest
) {
}