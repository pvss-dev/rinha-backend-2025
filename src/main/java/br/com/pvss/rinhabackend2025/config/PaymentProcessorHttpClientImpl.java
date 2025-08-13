package br.com.pvss.rinhabackend2025.config;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static java.net.http.HttpRequest.BodyPublishers.ofString;
import static java.time.Duration.ofMillis;

public record PaymentProcessorHttpClientImpl(String baseUrl, HttpClient httpClient) implements PaymentProcessorClient {

    @Override
    public boolean processPayment(String pagamento) {
        try {
            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .timeout(ofMillis(180))
                    .uri(URI.create(baseUrl + "/payments"))
                    .POST(ofString(pagamento))
                    .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .build();

            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            return response.statusCode() == 200;

        } catch (Exception ignored) {
            return false;
        }
    }
}