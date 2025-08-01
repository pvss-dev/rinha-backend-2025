package br.com.pvssdev.infrastructure.scheduler;

import br.com.pvssdev.infrastructure.client.DefaultHealthClient;
import br.com.pvssdev.infrastructure.client.FallbackHealthClient;
import br.com.pvssdev.infrastructure.client.dto.HealthStatus;
import io.quarkus.logging.Log;
import io.quarkus.scheduler.Scheduled;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.rest.client.inject.RestClient;

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

    @Scheduled(every = "5s", identity = "health-check-task")
    public Uni<Void> pollHealthStatus() {
        Uni<Void> defaultCheck = defaultHealthClient.checkHealth()
                .invoke(healthCache::updateDefaultStatus)
                .onFailure().invoke(failure -> {
                    Log.warn("Failed to check health of DEFAULT processor", failure);
                    healthCache.updateDefaultStatus(new HealthStatus(true, Integer.MAX_VALUE));
                })
                .replaceWithVoid();

        Uni<Void> fallbackCheck = fallbackHealthClient.checkHealth()
                .invoke(healthCache::updateFallbackStatus)
                .onFailure().invoke(failure -> {
                    Log.warn("Failed to check health of FALLBACK processor", failure);
                    healthCache.updateFallbackStatus(new HealthStatus(true, Integer.MAX_VALUE));
                })
                .replaceWithVoid();

        return Uni.combine().all().unis(defaultCheck, fallbackCheck).discardItems();
    }
}