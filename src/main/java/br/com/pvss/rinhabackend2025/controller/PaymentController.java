package br.com.pvss.rinhabackend2025.controller;

import br.com.pvss.rinhabackend2025.dto.PaymentProcessorRequest;
import br.com.pvss.rinhabackend2025.service.PaymentProcessorService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/payments")
public class PaymentController {

    private final PaymentProcessorService paymentService;

    public PaymentController(PaymentProcessorService paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping
    public ResponseEntity<Void> pay(@Valid @RequestBody PaymentProcessorRequest paymentRequest) {
        paymentService.processAndSavePayment(paymentRequest);
        return ResponseEntity.accepted().build();
    }
}