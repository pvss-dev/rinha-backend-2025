package br.com.pvss.rinhabackend2025.dto;

public enum ProcessorType {
    DEFAULT("http://payment-processor-default:8080"),
    FALLBACK("http://payment-processor-fallback:8080");

    private final String baseUrl;

    ProcessorType(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getBaseUrl() {
        return baseUrl;
    }
}