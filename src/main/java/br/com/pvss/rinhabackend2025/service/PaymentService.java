package br.com.pvss.rinhabackend2025.service;

import br.com.pvss.rinhabackend2025.client.PaymentProcessorClient;
import br.com.pvss.rinhabackend2025.dto.PaymentRequestDto;
import br.com.pvss.rinhabackend2025.dto.ProcessorPaymentRequest;
import br.com.pvss.rinhabackend2025.dto.ProcessorType;
import br.com.pvss.rinhabackend2025.exception.DuplicatePaymentException;
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

    private final MongoSummaryService summary;
    private final HealthCheckService health;
    private final PaymentProcessorClient client;

    private static final Duration PRIMARY_TIMEOUT = Duration.ofMillis(300);
    private static final Duration FALLBACK_TIMEOUT = Duration.ofMillis(350);

    public PaymentService(MongoSummaryService summary,
                          HealthCheckService health,
                          PaymentProcessorClient client) {
        this.summary = summary;
        this.health = health;
        this.client = client;
    }

    public Mono<Void> processPayment(PaymentRequestDto request) {
        var normalizedAmount = request.amount().setScale(2, RoundingMode.HALF_UP);
        Instant requestedAt = Instant.now();
        var payload = new ProcessorPaymentRequest(request.correlationId(), normalizedAmount, requestedAt);

        return health.getAvailableProcessor()
                .flatMap(processorType ->
                        routeAndPersist(processorType, payload)
                )
                .then();
    }

    private Mono<Void> routeAndPersist(ProcessorType primary, ProcessorPaymentRequest payload) {
        ProcessorType secondary = (primary == ProcessorType.DEFAULT) ? ProcessorType.FALLBACK : ProcessorType.DEFAULT;

        return attemptAndPersist(primary, payload, PRIMARY_TIMEOUT)
                .onErrorResume(err -> {
                    if (isDuplicate422(err)) {
                        return Mono.error(new DuplicatePaymentException());
                    }
                    if (isConnectivityOrServer(err)) {
                        log.warn("Primário {} falhou ({}). Tentando fallback {}.", primary, err.getClass().getSimpleName(), secondary);
                        return attemptAndPersist(secondary, payload, FALLBACK_TIMEOUT)
                                .onErrorResume(err2 -> {
                                    if (isDuplicate422(err2)) {
                                        return Mono.error(new DuplicatePaymentException());
                                    }
                                    log.error("Fallback {} falhou ({}).", secondary, err2.getClass().getSimpleName());
                                    return Mono.error(err2);
                                });
                    }
                    return Mono.error(err);
                });
    }

    private Mono<Void> attemptAndPersist(ProcessorType p, ProcessorPaymentRequest payload, Duration timeout) {
        return client.sendPayment(p, payload).timeout(timeout)
                .flatMap(usedProcessor ->
                        summary.persistPaymentSummary(usedProcessor, payload.amount(), payload.correlationId(), payload.requestedAt())
                );
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