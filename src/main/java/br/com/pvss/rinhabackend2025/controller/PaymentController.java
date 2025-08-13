package br.com.pvss.rinhabackend2025.controller;

import br.com.pvss.rinhabackend2025.config.PaymentProcessorClient;
import br.com.pvss.rinhabackend2025.dto.PaymentProcessorRequest;
import br.com.pvss.rinhabackend2025.dto.ProcessorPaymentRequest;
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
public class PaymentController {

    private final PaymentProcessorService service;
    private final PaymentProcessorService paymentProcessorService;

    public PaymentController(
            PaymentRepository repository,
            @Qualifier("paymentProcessorDefaultClient") PaymentProcessorClient paymentProcessorDefault,
            @Qualifier("paymentProcessorFallbackClient") PaymentProcessorClient paymentProcessorFallback, PaymentProcessorService paymentProcessorService
    ) {
        this.service = new PaymentProcessorService(repository, paymentProcessorDefault, paymentProcessorFallback);
        this.paymentProcessorService = paymentProcessorService;
    }

    @PostMapping
    public void pagar(@RequestBody PaymentProcessorRequest paymentRequest) {
        ProcessorPaymentRequest paymentProcessorRequest = new ProcessorPaymentRequest(
                paymentRequest.correlationId(), paymentRequest.amount(), Instant.now().truncatedTo(ChronoUnit.SECONDS));

        String paymentJson = service.convertObjectToJson(paymentProcessorRequest);
        CompletePaymentProcessor paymentProcessorCompleto = new CompletePaymentProcessor(paymentJson, paymentProcessorRequest);

        paymentProcessorService.adicionaNaFila(paymentProcessorCompleto);
    }
}