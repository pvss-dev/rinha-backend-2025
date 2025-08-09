package br.com.pvss.rinhabackend2025.service;

import br.com.pvss.rinhabackend2025.exception.DuplicatePaymentException;
import br.com.pvss.rinhabackend2025.client.PaymentProcessorClient;
import br.com.pvss.rinhabackend2025.dto.PaymentRequestDto;
import br.com.pvss.rinhabackend2025.dto.ProcessorPaymentRequest;
import br.com.pvss.rinhabackend2025.dto.ProcessorType;
import br.com.pvss.rinhabackend2025.model.ReceivedRequest;
import br.com.pvss.rinhabackend2025.repository.ReceivedRequestRepo;
import com.mongodb.DuplicateKeyException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;

@Service
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

    private final ReceivedRequestRepo receivedRepo;
    private final MongoSummaryService summary;
    private final HealthCheckService health;
    private final PaymentProcessorClient client;

    private static final Duration PRIMARY_TIMEOUT = Duration.ofMillis(650);
    private static final Duration FALLBACK_TIMEOUT = Duration.ofMillis(700);

    public PaymentService(ReceivedRequestRepo receivedRepo,
                          MongoSummaryService summary,
                          HealthCheckService health,
                          PaymentProcessorClient client) {
        this.receivedRepo = receivedRepo;
        this.summary = summary;
        this.health = health;
        this.client = client;
    }

    public Mono<Void> processPayment(PaymentRequestDto request) {
        var normalizedAmount = request.amount().setScale(2, RoundingMode.HALF_UP);

        Instant requestedAt = Instant.now();
        var payload = new ProcessorPaymentRequest(
                request.correlationId(), normalizedAmount, requestedAt
        );

        var rec = new ReceivedRequest(request.correlationId(), requestedAt);

        return receivedRepo.save(rec)
                .onErrorResume(DuplicateKeyException.class, e -> Mono.error(new DuplicatePaymentException()))
                .then(health.getAvailableProcessor())
                .flatMap(primary -> routeWithFallback(primary, payload))
                .flatMap(used ->
                        summary.persistPaymentSummary(used, normalizedAmount, request.correlationId(), requestedAt)
                );
    }

    private Mono<ProcessorType> routeWithFallback(ProcessorType primary, ProcessorPaymentRequest payload) {
        ProcessorType secondary = (primary == ProcessorType.DEFAULT) ? ProcessorType.FALLBACK : ProcessorType.DEFAULT;

        return attemptOnce(primary, payload, PRIMARY_TIMEOUT)
                .onErrorResume(err -> {
                    if (isDuplicate422(err)) return Mono.error(err);
                    if (isConnectivityOrServer(err)) {
                        log.warn("Primário {} falhou ({}). Tentando fallback {}.", primary, err.getClass().getSimpleName(), secondary);
                        return attemptOnce(secondary, payload, FALLBACK_TIMEOUT);
                    }
                    return Mono.error(err);
                });
    }

    private Mono<ProcessorType> attemptOnce(ProcessorType p, ProcessorPaymentRequest payload, Duration timeout) {
        return client.sendPayment(p, payload).timeout(timeout);
    }

    private static boolean isDuplicate422(Throwable t) {
        return (t instanceof WebClientResponseException w) && w.getStatusCode().value() == 422;
    }

    private static boolean isConnectivityOrServer(Throwable t) {
        if (t instanceof java.util.concurrent.TimeoutException) return true;
        if (t instanceof WebClientResponseException w) {
            int s = w.getStatusCode().value();
            return s == 429 || (s >= 500 && s <= 599);
        }
        return true;
    }
}