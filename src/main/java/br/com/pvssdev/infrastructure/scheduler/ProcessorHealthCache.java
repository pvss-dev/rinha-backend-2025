package br.com.pvssdev.infrastructure.scheduler;

import br.com.pvssdev.infrastructure.client.dto.HealthStatus;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.concurrent.atomic.AtomicReference;

@ApplicationScoped
public class ProcessorHealthCache {

    private final AtomicReference<HealthStatus> defaultStatus = new AtomicReference<>(new HealthStatus(false, 0));
    private final AtomicReference<HealthStatus> fallbackStatus = new AtomicReference<>(new HealthStatus(false, 0));

    public HealthStatus getDefaultStatus() {
        return defaultStatus.get();
    }

    public void updateDefaultStatus(HealthStatus status) {
        this.defaultStatus.set(status);
    }

    public HealthStatus getFallbackStatus() {
        return fallbackStatus.get();
    }

    public void updateFallbackStatus(HealthStatus status) {
        this.fallbackStatus.set(status);
    }
}