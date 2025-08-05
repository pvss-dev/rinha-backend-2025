package br.com.pvss.rinhabackend2025.client;

import br.com.pvss.rinhabackend2025.dto.PaymentDto;
import br.com.pvss.rinhabackend2025.dto.PaymentRequestDto;
import br.com.pvss.rinhabackend2025.dto.ProcessorType;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.time.format.DateTimeFormatter;

@Component
public class PaymentProcessorClient {

    private final WebClient defaultProcessorClient;
    private final WebClient fallbackProcessorClient;

    public PaymentProcessorClient(
            @Qualifier("defaultProcessorClient") WebClient defaultProcessorClient,
            @Qualifier("fallbackProcessorClient") WebClient fallbackProcessorClient
    ) {
        this.defaultProcessorClient = defaultProcessorClient;
        this.fallbackProcessorClient = fallbackProcessorClient;
    }

    public Mono<ProcessorType> sendPayment(ProcessorType type, PaymentRequestDto request) {
        String formattedDate = DateTimeFormatter.ISO_INSTANT.format(Instant.now());

        PaymentDto payload = new PaymentDto(request.correlationId(), request.amount(), formattedDate);

        WebClient client = switch (type) {
            case DEFAULT -> defaultProcessorClient;
            case FALLBACK -> fallbackProcessorClient;
        };

        return client
                .post()
                .uri("/payments")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(payload)
                .retrieve()
                .toBodilessEntity()
                .thenReturn(type)
                .onErrorResume(e -> {
                    System.err.println("Falha ao enviar pagamento para " + type + ": " + e.getMessage());
                    return Mono.error(e);
                });
    }
}