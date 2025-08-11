package br.com.pvss.rinhabackend2025.service;

import br.com.pvss.rinhabackend2025.client.PaymentProcessorClient;
import br.com.pvss.rinhabackend2025.dto.HealthResponse;
import br.com.pvss.rinhabackend2025.dto.ProcessorType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class HealthCheckService {

    private final PaymentProcessorClient client;

    private final Map<ProcessorType, HealthState> healthCache = new ConcurrentHashMap<>();

    public HealthCheckService(PaymentProcessorClient client) {
        this.client = client;
        healthCache.put(ProcessorType.DEFAULT, new HealthState(true, System.currentTimeMillis()));
        healthCache.put(ProcessorType.FALLBACK, new HealthState(true, System.currentTimeMillis()));
    }

    @Scheduled(fixedRate = 5000)
    public void performHealthCheck() {
        long now = System.currentTimeMillis();

        HealthResponse defaultHealth = client.checkHealth(ProcessorType.DEFAULT);
        healthCache.put(ProcessorType.DEFAULT, new HealthState(!defaultHealth.failing(), now));

        HealthResponse fallbackHealth = client.checkHealth(ProcessorType.FALLBACK);
        healthCache.put(ProcessorType.FALLBACK, new HealthState(!fallbackHealth.failing(), now));
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

    private record HealthState(boolean isHealthy, long timestamp) {
    }
}