package br.com.pvss.rinhabackend2025.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.http.HttpClient;

@Configuration
public class PaymentHttpClientConfig {

    @Bean("paymentProcessorDefaultClient")
    public PaymentProcessorManualClient paymentProcessorDefaultClient(
            HttpClient httpClient,
            @Value("${payment.processor.default.url}") String baseUrl
    ) {
        return new PaymentProcessorHttpClientImpl(baseUrl, httpClient);
    }

    @Bean("paymentProcessorFallbackClient")
    public PaymentProcessorManualClient paymentProcessorFallbackClient(
            HttpClient httpClient,
            @Value("${payment.processor.fallback.url}") String baseUrl
    ) {
        return new PaymentProcessorHttpClientImpl(baseUrl, httpClient);
    }
}