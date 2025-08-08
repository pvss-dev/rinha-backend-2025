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

    private final WebClient defaultClient;
    private final WebClient fallbackClient;

    public PaymentProcessorClient(@Qualifier("defaultProcessorClient") WebClient defaultClient,
                                  @Qualifier("fallbackProcessorClient") WebClient fallbackClient) {
        this.defaultClient = defaultClient;
        this.fallbackClient = fallbackClient;
    }

    public Mono<ProcessorType> sendPayment(ProcessorType type, ProcessorPaymentRequest payload) {
        WebClient client = (type == ProcessorType.DEFAULT) ? defaultClient : fallbackClient;
        return client.post()
                .uri("/payments")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(payload)
                .retrieve()
                .toBodilessEntity()
                .thenReturn(type);
    }
}