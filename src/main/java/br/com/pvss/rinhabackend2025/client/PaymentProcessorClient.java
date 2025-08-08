package br.com.pvss.rinhabackend2025.client;

import br.com.pvss.rinhabackend2025.dto.ProcessorPaymentRequest;
import br.com.pvss.rinhabackend2025.dto.ProcessorType;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

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

    public Mono<ProcessorType> sendPayment(ProcessorType type, ProcessorPaymentRequest payload) {
        WebClient client = (type == ProcessorType.DEFAULT) ? defaultProcessorClient : fallbackProcessorClient;

        return client.post()
                .uri("/payments")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(payload)
                .retrieve()
                .toBodilessEntity()
                .thenReturn(type);
    }
}