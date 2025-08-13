package br.com.pvss.rinhabackend2025.config;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import static java.net.http.HttpRequest.BodyPublishers.ofString;

public record PaymentProcessorHttpClientImpl(String baseUrl, HttpClient httpClient) implements PaymentProcessorClient {

    private static final Duration REQUEST_TIMEOUT = Duration.ofMillis(200);

    @Override
    public boolean processPayment(String pagamento) {
        try {
            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .timeout(REQUEST_TIMEOUT)
                    .uri(URI.create(baseUrl + "/payments"))
                    .POST(ofString(pagamento))
                    .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .build();

            return httpClient.sendAsync(httpRequest, HttpResponse.BodyHandlers.ofString())
                    .thenApply(HttpResponse::statusCode)
                    .thenApply(statusCode -> statusCode >= 200 && statusCode < 300)
                    .exceptionally(ex -> false)
                    .join();

        } catch (Exception ignored) {
            return false;
        }
    }
}