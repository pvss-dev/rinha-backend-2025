package br.com.pvss.rinhabackend2025.controller;

import br.com.pvss.rinhabackend2025.config.PaymentProcessorClient;
import br.com.pvss.rinhabackend2025.dto.PaymentProcessorRequest;
import br.com.pvss.rinhabackend2025.dto.PaymentRequest;
import br.com.pvss.rinhabackend2025.model.CompletePaymentProcessor;
import br.com.pvss.rinhabackend2025.repository.PaymentRepository;
import br.com.pvss.rinhabackend2025.service.PaymentProcessorService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@RestController
@RequestMapping("/payments")
public class PaymentProcessorController {

    private final PaymentProcessorService paymentProcessorService;

    public PaymentProcessorController(
            PaymentRepository paymentRepository,
            @Qualifier("paymentProcessorDefaultClient")
            PaymentProcessorClient paymentProcessorDefault,
            @Qualifier("paymentProcessorFallbackClient")
            PaymentProcessorClient paymentProcessorFallback
    ) {
        this.paymentProcessorService = new PaymentProcessorService(paymentRepository, paymentProcessorDefault, paymentProcessorFallback);
    }

    @PostMapping
    public void pagar(@RequestBody PaymentRequest paymentRequest) {
        PaymentProcessorRequest paymentProcessorRequest = new PaymentProcessorRequest(
                paymentRequest.correlationId(),
                paymentRequest.amount(),
                Instant.now().truncatedTo(ChronoUnit.SECONDS)
        );

        String paymentEmJson = paymentProcessorService.convertObjectToJson(paymentProcessorRequest);
        CompletePaymentProcessor paymentProcessorCompleto = new CompletePaymentProcessor(paymentEmJson, paymentProcessorRequest);

        paymentProcessorService.addToQueue(paymentProcessorCompleto);
    }
}