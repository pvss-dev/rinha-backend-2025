package br.com.pvss.rinhabackend2025.service;

import br.com.pvss.rinhabackend2025.client.PaymentProcessorClient;
import br.com.pvss.rinhabackend2025.dto.HealthResponse;
import br.com.pvss.rinhabackend2025.dto.ProcessorType;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

@Service
public class HealthCheckService {

    private static final Logger log = LoggerFactory.getLogger(HealthCheckService.class);
    private final PaymentProcessorClient client;
    private final Map<ProcessorType, HealthState> healthCache = new ConcurrentHashMap<>();
    private final ExecutorService healthCheckExecutor = Executors.newFixedThreadPool(2);

    public HealthCheckService(PaymentProcessorClient client) {
        this.client = client;
        healthCache.put(ProcessorType.DEFAULT, new HealthState(true, 0));
        healthCache.put(ProcessorType.FALLBACK, new HealthState(true, 0));
    }

    @Scheduled(fixedRate = 5000)
    public void performHealthCheck() {
        long now = System.currentTimeMillis();

        Future<HealthResponse> defaultFuture = healthCheckExecutor.submit(() -> client.checkHealth(ProcessorType.DEFAULT));
        Future<HealthResponse> fallbackFuture = healthCheckExecutor.submit(() -> client.checkHealth(ProcessorType.FALLBACK));

        try {
            HealthResponse defaultHealth = defaultFuture.get();
            healthCache.put(ProcessorType.DEFAULT, new HealthState(!defaultHealth.failing(), now));
        } catch (Exception e) {
            log.warn("Health check falhou para o processador DEFAULT. Considerando DOWN.");
            healthCache.put(ProcessorType.DEFAULT, new HealthState(false, now));
        }

        try {
            HealthResponse fallbackHealth = fallbackFuture.get();
            healthCache.put(ProcessorType.FALLBACK, new HealthState(!fallbackHealth.failing(), now));
        } catch (Exception e) {
            log.warn("Health check falhou para o processador FALLBACK. Considerando DOWN.");
            healthCache.put(ProcessorType.FALLBACK, new HealthState(false, now));
        }
    }

    public ProcessorType getAvailableProcessor() {
        if (healthCache.getOrDefault(ProcessorType.DEFAULT, new HealthState(false, 0)).isHealthy()) {
            return ProcessorType.DEFAULT;
        }
        if (healthCache.getOrDefault(ProcessorType.FALLBACK, new HealthState(false, 0)).isHealthy()) {
            return ProcessorType.FALLBACK;
        }
        return null;
    }

    @PreDestroy
    public void shutdown() {
        healthCheckExecutor.shutdown();
    }

    private record HealthState(boolean isHealthy, long timestamp) {
    }
}