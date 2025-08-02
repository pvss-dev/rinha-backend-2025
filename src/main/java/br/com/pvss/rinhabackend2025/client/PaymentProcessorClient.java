package br.com.pvss.rinhabackend2025.client;

import br.com.pvss.rinhabackend2025.dto.PaymentDto;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import io.github.resilience4j.retry.annotation.Retry;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Component
public class PaymentProcessorClient {

    private final WebClient webClient;

    public PaymentProcessorClient(WebClient.Builder builder) {
        this.webClient = builder.baseUrl("http://payment-processor").build();
    }

    @CircuitBreaker(name = "processor")
    @Retry(name = "processor")
    @RateLimiter(name = "processor")
    public Mono<Void> process(PaymentDto dto) {
        return webClient.post()
                .uri("/payments")
                .bodyValue(dto)
                .retrieve()
                .toBodilessEntity()
                .then();
    }
}