package br.com.pvss.rinhabackend2025.model;

import br.com.pvss.rinhabackend2025.dto.ProcessorPaymentRequest;

public record CompletePaymentProcessor(
        String paymentJson,
        ProcessorPaymentRequest request
) {
}
