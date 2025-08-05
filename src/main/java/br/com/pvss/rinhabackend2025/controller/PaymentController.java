package br.com.pvss.rinhabackend2025.controller;

import br.com.pvss.rinhabackend2025.dto.PaymentRequestDto;
import br.com.pvss.rinhabackend2025.service.PaymentService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping("/payments")
    public Mono<ResponseEntity<Void>> createPayment(@RequestBody PaymentRequestDto request) {
        return paymentService.processPayment(request)
                .thenReturn(ResponseEntity.accepted().build());
    }
}