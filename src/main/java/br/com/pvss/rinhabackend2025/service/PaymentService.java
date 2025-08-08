package br.com.pvss.rinhabackend2025.service;

import br.com.pvss.rinhabackend2025.client.PaymentProcessorClient;
import br.com.pvss.rinhabackend2025.dto.PaymentRequestDto;
import br.com.pvss.rinhabackend2025.dto.ProcessorPaymentRequest;
import br.com.pvss.rinhabackend2025.dto.ProcessorType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.time.Instant;

@Service
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

    private final HealthCheckService healthCheckService;
    private final PaymentProcessorClient paymentProcessorClient;
    private final RedisSummaryService redisSummaryService;

    public PaymentService(HealthCheckService healthCheckService,
                          PaymentProcessorClient paymentProcessorClient,
                          RedisSummaryService redisSummaryService) {
        this.healthCheckService = healthCheckService;
        this.paymentProcessorClient = paymentProcessorClient;
        this.redisSummaryService = redisSummaryService;
    }

    public Mono<Void> processPayment(PaymentRequestDto request) {
        Instant requestedAt = Instant.now();
        ProcessorPaymentRequest payload = new ProcessorPaymentRequest(
                request.correlationId(),
                request.amount(),
                requestedAt
        );

        return healthCheckService.getAvailableProcessor()
                .flatMap(primary -> {
                    ProcessorType fallback = (primary == ProcessorType.DEFAULT)
                            ? ProcessorType.FALLBACK : ProcessorType.DEFAULT;

                    return attemptPayment(primary, payload)
                            .onErrorResume(err -> {
                                if (!isRetryableError(err)) return Mono.error(err);
                                log.warn("Primário {} falhou; tentando fallback {}. Causa: {}", primary, fallback, err.toString());
                                return attemptPayment(fallback, payload);
                            });
                });
    }

    private Mono<Void> attemptPayment(ProcessorType processor, ProcessorPaymentRequest payload) {
        return paymentProcessorClient.sendPayment(processor, payload)
                .retryWhen(Retry.backoff(2, Duration.ofMillis(50))
                        .filter(this::isRetryableError))
                .flatMap(used ->
                        redisSummaryService.persistPaymentSummary(
                                used,
                                payload.amount(),
                                payload.correlationId(),
                                payload.requestedAt()
                        )
                );
    }

    private boolean isRetryableError(Throwable error) {
        if (error instanceof java.util.concurrent.TimeoutException) return true;
        if (error instanceof WebClientResponseException wcre) {
            int s = wcre.getStatusCode().value();
            return s == 429 || (s >= 500 && s < 600);
        }
        return false;
    }
}