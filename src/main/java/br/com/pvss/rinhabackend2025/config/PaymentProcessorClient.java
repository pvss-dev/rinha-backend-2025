package br.com.pvss.rinhabackend2025.config;

public interface PaymentProcessorClient {
    boolean processPayment(String request);
}