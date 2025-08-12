package br.com.pvss.rinhabackend2025.client;

import br.com.pvss.rinhabackend2025.dto.HealthResponse;
import br.com.pvss.rinhabackend2025.dto.ProcessorPaymentRequest;
import br.com.pvss.rinhabackend2025.dto.ProcessorType;
import br.com.pvss.rinhabackend2025.dto.SendResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

@Component
public class PaymentProcessorClient {

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String defaultUrl;
    private final String fallbackUrl;
    private final Duration paymentTimeout;
    private final Duration healthTimeout;

    public PaymentProcessorClient(
            HttpClient httpClient,
            ObjectMapper objectMapper,
            @Value("${payment.processor.default.url}") String defaultUrl,
            @Value("${payment.processor.fallback.url}") String fallbackUrl,
            @Value("${payment.timeout.ms}") int paymentTimeoutMs,
            @Value("${health.timeout.ms}") int healthTimeoutMs
    ) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
        this.defaultUrl = defaultUrl;
        this.fallbackUrl = fallbackUrl;
        this.paymentTimeout = Duration.ofMillis(paymentTimeoutMs);
        this.healthTimeout = Duration.ofMillis(healthTimeoutMs);
    }

    public SendResult sendPayment(ProcessorType type, ProcessorPaymentRequest payload) {
        try {
            final String url = (type == ProcessorType.DEFAULT) ? defaultUrl : fallbackUrl;
            final String json = objectMapper.writeValueAsString(payload);

            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .timeout(paymentTimeout)
                    .uri(URI.create(url + "/payments"))
                    .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();

            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            int sc = response.statusCode();
            if (sc >= 200 && sc < 300) return SendResult.SUCCESS;
            if (sc == 422) return SendResult.DUPLICATE;
            return SendResult.RETRIABLE_FAILURE;
        } catch (Exception e) {
            return SendResult.RETRIABLE_FAILURE;
        }
    }

    public HealthResponse checkHealth(ProcessorType type) {
        String url = (type == ProcessorType.DEFAULT) ? defaultUrl : fallbackUrl;
        HttpRequest httpRequest = HttpRequest.newBuilder()
                .timeout(healthTimeout)
                .uri(URI.create(url + "/payments/service-health"))
                .GET()
                .build();
        try {
            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                return objectMapper.readValue(response.body(), HealthResponse.class);
            }
        } catch (Exception ignored) {
        }
        return new HealthResponse(true, Integer.MAX_VALUE);
    }

    public boolean wasProcessed(ProcessorType type, java.util.UUID id) {
        String base = (type == ProcessorType.DEFAULT) ? defaultUrl : fallbackUrl;
        HttpRequest req = HttpRequest.newBuilder()
                .timeout(Duration.ofMillis(500))
                .uri(URI.create(base + "/payments/" + id))
                .GET()
                .build();
        try {
            HttpResponse<Void> resp = httpClient.send(req, HttpResponse.BodyHandlers.discarding());
            return resp.statusCode() == 200;
        } catch (Exception e) {
            return false;
        }
    }
}