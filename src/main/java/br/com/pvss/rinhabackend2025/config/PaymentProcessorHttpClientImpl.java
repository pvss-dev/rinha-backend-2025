package br.com.pvss.rinhabackend2025.config;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

public record PaymentProcessorHttpClientImpl(
        String baseUrl,
        HttpClient httpClient
) implements PaymentProcessorManualClient {

    @Override
    public CompletableFuture<Boolean> processPaymentAsync(String requestJson) {
        HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(this.baseUrl + "/payments"))
                .timeout(Duration.ofSeconds(2))
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .POST(HttpRequest.BodyPublishers.ofString(requestJson))
                .build();

        return this.httpClient.sendAsync(httpRequest, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> response.statusCode() >= 200 && response.statusCode() < 300);
    }
}