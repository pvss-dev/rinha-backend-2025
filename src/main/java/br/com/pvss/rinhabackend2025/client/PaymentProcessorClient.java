package br.com.pvss.rinhabackend2025.client;

import br.com.pvss.rinhabackend2025.dto.PaymentDto;
import br.com.pvss.rinhabackend2025.dto.PaymentRequestDto;
import br.com.pvss.rinhabackend2025.dto.ProcessorType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Instant;

@Component
public class PaymentProcessorClient {

    private final WebClient.Builder webClientBuilder;

    public PaymentProcessorClient(WebClient.Builder webClientBuilder) {
        this.webClientBuilder = webClientBuilder;
    }

    public Mono<ProcessorType> sendPayment(ProcessorType type, PaymentRequestDto request) {
        PaymentDto payload = new PaymentDto(request.correlationId(), request.amount(), Instant.now());

        return webClientBuilder.build()
                .post()
                .uri(type.getBaseUrl() + "/payments")
                .bodyValue(payload)
                .retrieve()
                .bodyToMono(String.class)
                .thenReturn(type);
    }
}