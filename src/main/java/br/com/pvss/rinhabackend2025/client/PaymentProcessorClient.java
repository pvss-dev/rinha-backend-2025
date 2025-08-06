package br.com.pvss.rinhabackend2025.client;

import br.com.pvss.rinhabackend2025.dto.PaymentDto;
import br.com.pvss.rinhabackend2025.dto.PaymentRequestDto;
import br.com.pvss.rinhabackend2025.dto.ProcessorType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

@Component
public class PaymentProcessorClient {

    private static final Logger log = LoggerFactory.getLogger(PaymentProcessorClient.class);
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
        Map<String, Object> payload = Map.of(
                "correlationId", request.correlationId().toString(),
                "amount", request.amount(),
                "requestedAt", Instant.now().toString()
        );

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
                .timeout(Duration.ofSeconds(8))
                .thenReturn(type)
                .doOnSuccess(t -> log.debug("Pagamento enviado com sucesso para {}", type))
                .onErrorResume(e -> {
                    log.warn("Falha ao enviar pagamento para {}: {}", type, e.getMessage());
                    return Mono.error(e);
                });
    }
}