package br.com.pvss.rinhabackend2025.config;

import java.util.concurrent.CompletableFuture;

public interface PaymentProcessorManualClient {
    CompletableFuture<Boolean> processPaymentAsync(String requestJson);
}