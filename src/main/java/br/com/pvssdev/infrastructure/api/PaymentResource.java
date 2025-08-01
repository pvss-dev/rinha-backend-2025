package br.com.pvssdev.infrastructure.api;

import br.com.pvssdev.application.dto.PaymentRequestDto;
import br.com.pvssdev.application.service.PaymentService;
import io.quarkus.hibernate.reactive.panache.common.WithTransaction;
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
    @WithTransaction
    public Uni<Response> createPayment(PaymentRequestDto request) {
        return paymentService.processPayment(request)
                .map(v -> Response.noContent().build())
                .onFailure().recoverWithItem(e -> {
                    return Response.status(Response.Status.SERVICE_UNAVAILABLE).build();
                });
    }

    @GET
    @Path("/payments-summary")
    @Produces(MediaType.APPLICATION_JSON)
    @WithTransaction
    public Uni<Response> getSummary(@QueryParam("from") String fromStr, @QueryParam("to") String toStr) {
        try {
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