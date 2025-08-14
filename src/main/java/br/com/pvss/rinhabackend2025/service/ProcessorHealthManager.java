package br.com.pvss.rinhabackend2025.service;

import br.com.pvss.rinhabackend2025.dto.HealthStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class ProcessorHealthManager {

    private static final Logger log = LoggerFactory.getLogger(ProcessorHealthManager.class);
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String defaultHealthUrl;
    private final String fallbackHealthUrl;
    private final AtomicReference<HealthStatus> defaultStatus = new AtomicReference<>(new HealthStatus(false, 0));
    private final AtomicReference<HealthStatus> fallbackStatus = new AtomicReference<>(new HealthStatus(false, 0));

    public ProcessorHealthManager(
            HttpClient httpClient,
            ObjectMapper objectMapper,
            @Value("${payment.processor.default.url}/payments/service-health") String defaultHealthUrl,
            @Value("${payment.processor.fallback.url}/payments/service-health") String fallbackHealthUrl
    ) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
        this.defaultHealthUrl = defaultHealthUrl;
        this.fallbackHealthUrl = fallbackHealthUrl;
    }

    @PostConstruct
    public void initialCheck() {
        log.info("Executando verificação de saúde inicial...");
        checkProcessorsHealth();
    }

    @Scheduled(
            initialDelayString = "${healthcheck.initial.delay.ms}",
            fixedRateString = "${healthcheck.rate.ms}"
    )
    public void checkProcessorsHealth() {
        log.trace("Iniciando verificação de saúde dos processadores...");

        fetchHealthStatus(defaultHealthUrl, "Default").thenAccept(defaultStatus::set);
        fetchHealthStatus(fallbackHealthUrl, "Fallback").thenAccept(fallbackStatus::set);
    }

    private CompletableFuture<HealthStatus> fetchHealthStatus(String url, String processorName) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(2))
                    .GET()
                    .build();

            return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                    .thenApply(response -> {
                        if (response.statusCode() == 200) {
                            try {
                                HealthStatus status = objectMapper.readValue(response.body(), HealthStatus.class);
                                log.info("Saúde do processador {} atualizada: {}", processorName, status);
                                return status;
                            } catch (IOException e) {
                                log.warn("Falha ao parsear JSON de saúde para {}. Body: {}", processorName, response.body(), e);
                                return HealthStatus.UNHEALTHY;
                            }
                        }

                        if (response.statusCode() == 429) {
                            log.warn("Rate limit atingido para o processador {}! Aumente o intervalo de health check.", processorName);
                        } else {
                            log.warn("Endpoint de saúde do processador {} retornou status {}.", processorName, response.statusCode());
                        }
                        return HealthStatus.UNHEALTHY;
                    })
                    .exceptionally(ex -> {
                        log.error("Erro de comunicação ao verificar saúde do processador {}. Causa: {}", processorName, ex.getMessage());
                        return HealthStatus.UNHEALTHY;
                    });
        } catch (Exception e) {
            log.error("Erro inesperado ao criar requisição de saúde para {}.", processorName, e);
            return CompletableFuture.completedFuture(HealthStatus.UNHEALTHY);
        }
    }

    public HealthStatus getDefaultStatus() {
        return defaultStatus.get();
    }

    public HealthStatus getFallbackStatus() {
        return fallbackStatus.get();
    }
}