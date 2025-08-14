package br.com.pvss.rinhabackend2025.controller;

import br.com.pvss.rinhabackend2025.dto.PaymentRequest;
import br.com.pvss.rinhabackend2025.exception.PaymentProcessingException;
import br.com.pvss.rinhabackend2025.service.PaymentService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping("/payments")
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping
    public CompletableFuture<ResponseEntity<Object>> createPayment(@RequestBody @Valid PaymentRequest request) {
        return paymentService.processPayment(request)
                .thenApply(v -> ResponseEntity.accepted().build())
                .exceptionally(ex -> {
                    if (ex.getCause() instanceof PaymentProcessingException) {
                        return ResponseEntity.status(503).build();
                    }
                    return ResponseEntity.internalServerError().build();
                });
    }
}