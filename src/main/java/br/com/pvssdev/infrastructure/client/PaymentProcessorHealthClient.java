package br.com.pvssdev.infrastructure.client;

import br.com.pvssdev.infrastructure.client.dto.HealthStatus;
import io.smallrye.mutiny.Uni;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import org.eclipse.microprofile.faulttolerance.Timeout;

@Path("/payments/service-health")
public interface PaymentProcessorHealthClient {

    @GET
    @Timeout(500)
    Uni<HealthStatus> checkHealth();
}