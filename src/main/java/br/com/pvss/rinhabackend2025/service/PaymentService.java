package br.com.pvss.rinhabackend2025.service;

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

import java.time.Duration;
import java.time.Instant;

@Service
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

    private final ReceivedRequestRepo receivedRepo;
    private final MongoSummaryService summary;
    private final HealthCheckService health;
    private final PaymentProcessorClient client;

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
        Instant requestedAt = Instant.now();
        ProcessorPaymentRequest payload = new ProcessorPaymentRequest(
                request.correlationId(), request.amount(), requestedAt
        );

        var rec = new ReceivedRequest(request.correlationId(), requestedAt);

        return receivedRepo.save(rec)
                .onErrorResume(DuplicateKeyException.class, e -> Mono.empty())
                .then(health.getAvailableProcessor())
                .flatMap(primary -> attemptOnce(primary, payload)
                        .onErrorResume(err -> {
                            if (isRetryable(err)) {
                                return attemptOnce(primary, payload);
                            }
                            return Mono.error(err);
                        })
                        .onErrorResume(err -> {
                            if (isServerOrRate(err)) {
                                ProcessorType fb = (primary == ProcessorType.DEFAULT) ? ProcessorType.FALLBACK : ProcessorType.DEFAULT;
                                log.warn("Primário {} falhou com 5xx/429. Fallback: {}", primary, fb);
                                return attemptOnce(fb, payload);
                            }
                            return Mono.error(err);
                        })
                )
                .flatMap(used -> summary.persistPaymentSummary(
                        used, request.amount(), request.correlationId(), requestedAt
                ));
    }

    private Mono<ProcessorType> attemptOnce(ProcessorType p, ProcessorPaymentRequest payload) {
        return client.sendPayment(p, payload).timeout(Duration.ofMillis(1500));
    }

    private static boolean isRetryable(Throwable t) {
        return t instanceof java.util.concurrent.TimeoutException || isServerOrRate(t);
    }

    private static boolean isServerOrRate(Throwable t) {
        return (t instanceof WebClientResponseException w) &&
               (w.getStatusCode().is5xxServerError() || w.getStatusCode().value() == 429);
    }
}