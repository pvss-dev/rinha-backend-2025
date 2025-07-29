package br.com.pvssdev.infrastructure.api;

import br.com.pvssdev.application.dto.PaymentRequestDto;
import br.com.pvssdev.application.service.PaymentService;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.time.Instant;
import java.time.format.DateTimeParseException;

@Path("/")
public class PaymentResource {

    @Inject
    PaymentService paymentService;

    @POST
    @Path("/payments")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Uni<Response> createPayment(PaymentRequestDto request) {
        // Validação básica de entrada pode ser adicionada aqui com @Valid
        return paymentService.processPayment(request)
                .map(v -> Response.noContent().build()) // Retorna 204 No Content em caso de sucesso
                .onFailure().recoverWithItem(e -> {
                    // Resposta genérica para falha de processamento (ex: ambos os processadores offline)
                    return Response.status(Response.Status.SERVICE_UNAVAILABLE).build();
                });
    }

    @GET
    @Path("/payments-summary") // [cite: 185]
    @Produces(MediaType.APPLICATION_JSON)
    public Uni<Response> getSummary(@QueryParam("from") String fromStr, @QueryParam("to") String toStr) {
        try {
            // Se os parâmetros não forem fornecidos, use um intervalo padrão amplo.
            Instant from = (fromStr != null) ? Instant.parse(fromStr) : Instant.EPOCH;
            Instant to = (toStr != null) ? Instant.parse(toStr) : Instant.now();

            return paymentService.getSummary(from, to)
                    .map(summary -> Response.ok(summary).build());
        } catch (DateTimeParseException e) {
            return Uni.createFrom().item(Response.status(Response.Status.BAD_REQUEST)
                    .entity("Invalid timestamp format. Use ISO format like 2025-07-29T10:15:30.00Z")
                    .build());
        }
    }
}