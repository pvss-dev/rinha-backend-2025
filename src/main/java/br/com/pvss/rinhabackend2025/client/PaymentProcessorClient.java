package br.com.pvss.rinhabackend2025.client;

import br.com.pvss.rinhabackend2025.dto.PaymentRequestDto;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Component
public class PaymentProcessorClient {

    @CircuitBreaker(name = "processor")
    @Retry(name = "processor")
    public Mono<Void> process(PaymentRequestDto dto, WebClient client) {
        return client.post()
                .uri("/payments")
                .bodyValue(dto)
                .retrieve()
                .toBodilessEntity()
                .then();
    }
}