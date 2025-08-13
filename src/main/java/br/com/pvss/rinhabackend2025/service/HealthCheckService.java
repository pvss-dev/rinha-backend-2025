package br.com.pvss.rinhabackend2025.service;

import br.com.pvss.rinhabackend2025.client.PaymentProcessorClient;
import br.com.pvss.rinhabackend2025.dto.HealthResponse;
import br.com.pvss.rinhabackend2025.dto.HealthState;
import br.com.pvss.rinhabackend2025.dto.ProcessorType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class HealthCheckService {

    private static final Logger log = LoggerFactory.getLogger(HealthCheckService.class);

    private final PaymentProcessorClient client;
    private final Map<ProcessorType, HealthState> healthCache = new ConcurrentHashMap<>();

    @Value("${payment.timeout.ms}")
    private int paymentTimeoutMs;

    private final boolean healthEnabled;
    private final long healthStaleMs;

    public HealthCheckService(
            PaymentProcessorClient client,
            @Value("${health.enabled}") boolean healthEnabled,
            @Value("${health.stale.ms}") long healthStaleMs
    ) {
        this.client = client;
        this.healthEnabled = healthEnabled;
        this.healthStaleMs = healthStaleMs;
        long now = System.currentTimeMillis();
        healthCache.put(ProcessorType.DEFAULT, new HealthState(false, Integer.MAX_VALUE, now));
        healthCache.put(ProcessorType.FALLBACK, new HealthState(false, Integer.MAX_VALUE, now));
    }

    @Scheduled(
            fixedRateString = "${health.fixed.rate.ms}",
            initialDelayString = "${health.default.initial.delay.ms}"
    )
    public void checkDefault() {
        checkAndUpdate(ProcessorType.DEFAULT);
    }

    @Scheduled(
            fixedRateString = "${health.fixed.rate.ms}",
            initialDelayString = "${health.fallback.initial.delay.ms}"
    )
    public void checkFallback() {
        checkAndUpdate(ProcessorType.FALLBACK);
    }

    private void checkAndUpdate(ProcessorType type) {
        if (!healthEnabled) return;

        long now = System.currentTimeMillis();
        HealthResponse hr = client.checkHealth(type);

        if (hr != null) {
            healthCache.put(type, new HealthState(!hr.failing(), hr.minResponseTime(), now));
        } else {
            log.debug("Health check sem sucesso para {} (mantendo último estado).", type);
        }
    }

    public ProcessorType getAvailableProcessor() {
        long now = System.currentTimeMillis();
        HealthState d = healthCache.get(ProcessorType.DEFAULT);
        HealthState f = healthCache.get(ProcessorType.FALLBACK);

        boolean dIsFreshAndHealthy = d != null && (now - d.timestamp()) <= healthStaleMs && d.healthy() && d.minResponseTime() <= paymentTimeoutMs;

        boolean fIsFreshAndHealthy = f != null && (now - f.timestamp()) <= healthStaleMs && f.healthy() && f.minResponseTime() <= paymentTimeoutMs;

        if (dIsFreshAndHealthy) {
            return ProcessorType.DEFAULT;
        }

        if (fIsFreshAndHealthy) {
            return ProcessorType.FALLBACK;
        }

        return null;
    }
}