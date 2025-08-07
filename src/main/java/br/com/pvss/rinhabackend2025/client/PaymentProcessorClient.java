package br.com.pvss.rinhabackend2025.client;

import br.com.pvss.rinhabackend2025.dto.PaymentRequestDto;
import br.com.pvss.rinhabackend2025.dto.ProcessorPaymentRequest;
import br.com.pvss.rinhabackend2025.dto.ProcessorType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Instant;

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
        ProcessorPaymentRequest payload = new ProcessorPaymentRequest(
                request.correlationId(),
                request.amount(),
                Instant.now()
        );

        WebClient client = switch (type) {
            case DEFAULT -> defaultProcessorClient;
            case FALLBACK -> fallbackProcessorClient;
        };

        return client.post()
                .uri("/payments")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(payload)
                .retrieve()
                .toBodilessEntity()
                .thenReturn(type)
                .doOnError(e -> log.warn("Falha ao enviar a {}: {}", type, e.getMessage()))
                .onErrorResume(e -> {
                    if (type == ProcessorType.DEFAULT) {
                        log.info("Tentando fallback para pagamento {}", request.correlationId());
                        return sendPayment(ProcessorType.FALLBACK, request);
                    }
                    return Mono.error(e);
                });
    }
}