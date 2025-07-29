package br.com.pvssdev.infrastructure.client;

import br.com.pvssdev.infrastructure.client.dto.ProcessorRequest;
import br.com.pvssdev.infrastructure.client.dto.ProcessorResponse;
import io.smallrye.mutiny.Uni;
import org.eclipse.microprofile.faulttolerance.CircuitBreaker;
import org.eclipse.microprofile.faulttolerance.Timeout;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;

@Path("/payments")
@RegisterRestClient(configKey = "fallback-processor")
public interface FallbackPaymentProcessorClient {
    @POST
    @Timeout(2000)
    @CircuitBreaker(requestVolumeThreshold = 20, failureRatio = 0.5, delay = 5000)
    Uni<ProcessorResponse> process(ProcessorRequest request);
}
