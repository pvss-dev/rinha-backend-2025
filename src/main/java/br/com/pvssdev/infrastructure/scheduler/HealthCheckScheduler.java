package br.com.pvssdev.infrastructure.scheduler;

import br.com.pvssdev.infrastructure.client.DefaultHealthClient;
import br.com.pvssdev.infrastructure.client.FallbackHealthClient;
import br.com.pvssdev.infrastructure.client.dto.HealthStatus;
import io.quarkus.logging.Log;
import io.quarkus.scheduler.Scheduled;
import io.smallrye.common.annotation.Blocking;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.rest.client.inject.RestClient;

import java.time.Duration;

@ApplicationScoped
public class HealthCheckScheduler {

    @Inject
    ProcessorHealthCache healthCache;

    @Inject
    @RestClient
    DefaultHealthClient defaultHealthClient;

    @Inject
    @RestClient
    FallbackHealthClient fallbackHealthClient;

    @Scheduled(every = "5s")
    @Blocking
    public void pollHealthStatus() {
        try {
            HealthStatus s = defaultHealthClient
                    .checkHealth()
                    .await()
                    .atMost(Duration.ofSeconds(2));
            healthCache.updateDefaultStatus(s);
        } catch (Exception t) {
            Log.warn("Failed to check health of DEFAULT processor", t);
            healthCache.updateDefaultStatus(new HealthStatus(true, Integer.MAX_VALUE));
        }

        try {
            HealthStatus s = fallbackHealthClient
                    .checkHealth()
                    .await()
                    .atMost(Duration.ofSeconds(2));
            healthCache.updateFallbackStatus(s);
        } catch (Exception t) {
            Log.warn("Failed to check health of FALLBACK processor", t);
            healthCache.updateFallbackStatus(new HealthStatus(true, Integer.MAX_VALUE));
        }
    }
}