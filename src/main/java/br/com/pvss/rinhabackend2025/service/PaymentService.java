package br.com.pvss.rinhabackend2025.service;

import br.com.pvss.rinhabackend2025.client.PaymentProcessorClient;
import br.com.pvss.rinhabackend2025.dto.PaymentRequestDto;
import br.com.pvss.rinhabackend2025.dto.ProcessorType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.time.Duration;

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
        return redisSummaryService.isAlreadyProcessed(request.correlationId().toString())
                .flatMap(alreadyProcessed -> {
                    if (Boolean.TRUE.equals(alreadyProcessed)) {
                        log.info("Pagamento já processado (idempotente): {}", request.correlationId());
                        return Mono.empty();
                    }
                    return processPaymentWithFallback(request);
                });
    }

    private Mono<Void> processPaymentWithFallback(PaymentRequestDto request) {
        return healthCheckService.getAvailableProcessor()
                .flatMap(primaryProcessor -> {
                    ProcessorType fallbackProcessor = (primaryProcessor == ProcessorType.DEFAULT)
                            ? ProcessorType.FALLBACK : ProcessorType.DEFAULT;

                    return attemptPayment(primaryProcessor, request)
                            .onErrorResume(primaryError -> {
                                log.warn("Tentativa primária com {} falhou. Tentando fallback: {}", primaryProcessor, fallbackProcessor, primaryError);
                                return attemptPayment(fallbackProcessor, request);
                            });
                });
    }

    private Mono<Void> attemptPayment(ProcessorType processor, PaymentRequestDto request) {
        return paymentProcessorClient.sendPayment(processor, request)
                .retryWhen(Retry.backoff(2, Duration.ofMillis(100))
                        .maxBackoff(Duration.ofMillis(500))
                        .filter(this::isRetryableError)
                        .doBeforeRetry(retrySignal -> log.warn("Retentativa para {} - Tentativa #{}. Causa: {}",
                                processor,
                                retrySignal.totalRetries() + 1,
                                retrySignal.failure().getMessage()))
                )
                .flatMap(usedProcessor ->
                        redisSummaryService.persistPaymentSummary(usedProcessor, request.amount(), request.correlationId()));
    }

    private boolean isRetryableError(Throwable error) {
        if (error instanceof java.util.concurrent.TimeoutException) return true;
        if (error instanceof WebClientResponseException wcre) {
            return wcre.getStatusCode().is5xxServerError() || wcre.getStatusCode().value() == 429;
        }
        return false;
    }
}