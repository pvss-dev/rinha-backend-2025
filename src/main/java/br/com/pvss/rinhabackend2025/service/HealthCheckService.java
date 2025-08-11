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

    private record HealthState(boolean healthy, int minResponseTime, long timestamp) {
    }

    public HealthCheckService(PaymentProcessorClient client) {
        this.client = client;
        long now = System.currentTimeMillis();
        healthCache.put(ProcessorType.DEFAULT, new HealthState(false, Integer.MAX_VALUE, now));
        healthCache.put(ProcessorType.FALLBACK, new HealthState(false, Integer.MAX_VALUE, now));
    }

    @Scheduled(fixedRate = 5000)
    public void performHealthCheck() {
        long now = System.currentTimeMillis();

        Future<HealthResponse> df = healthCheckExecutor.submit(() -> client.checkHealth(ProcessorType.DEFAULT));
        Future<HealthResponse> ff = healthCheckExecutor.submit(() -> client.checkHealth(ProcessorType.FALLBACK));

        try {
            HealthResponse dh = df.get();
            healthCache.put(ProcessorType.DEFAULT, new HealthState(!dh.failing(), dh.minResponseTime(), now));
        } catch (Exception e) {
            log.warn("Health check falhou para DEFAULT. Considerando DOWN.", e);
            healthCache.put(ProcessorType.DEFAULT, new HealthState(false, Integer.MAX_VALUE, now));
        }

        try {
            HealthResponse fh = ff.get();
            healthCache.put(ProcessorType.FALLBACK, new HealthState(!fh.failing(), fh.minResponseTime(), now));
        } catch (Exception e) {
            log.warn("Health check falhou para FALLBACK. Considerando DOWN.", e);
            healthCache.put(ProcessorType.FALLBACK, new HealthState(false, Integer.MAX_VALUE, now));
        }
    }

    public ProcessorType getAvailableProcessor() {
        HealthState d = healthCache.get(ProcessorType.DEFAULT);
        HealthState f = healthCache.get(ProcessorType.FALLBACK);

        boolean dOk = d != null && d.healthy;
        boolean fOk = f != null && f.healthy;

        if (dOk && fOk) {
            return d.minResponseTime <= f.minResponseTime ? ProcessorType.DEFAULT : ProcessorType.FALLBACK;
        }
        if (dOk) return ProcessorType.DEFAULT;
        if (fOk) return ProcessorType.FALLBACK;
        return null;
    }

    @PreDestroy
    public void shutdown() {
        healthCheckExecutor.shutdown();
    }
}