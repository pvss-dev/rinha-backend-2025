package br.com.pvss.rinhabackend2025.controller;

import br.com.pvss.rinhabackend2025.dto.PaymentRequestDto;
import br.com.pvss.rinhabackend2025.service.PaymentQueueService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
public class PaymentController {

    private final PaymentQueueService paymentQueueService;

    public PaymentController(PaymentQueueService paymentQueueService) {
        this.paymentQueueService = paymentQueueService;
    }

    @PostMapping("/payments")
    public Mono<ResponseEntity<Void>> createPayment(@RequestBody PaymentRequestDto request) {
        if (request == null || request.correlationId() == null || request.amount() == null) {
            return Mono.just(ResponseEntity.badRequest().build());
        }

        return paymentQueueService.enqueuePayment(request)
                .thenReturn(ResponseEntity.accepted().build());
    }
}