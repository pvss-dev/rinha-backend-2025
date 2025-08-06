package br.com.pvss.rinhabackend2025.service;

import br.com.pvss.rinhabackend2025.client.PaymentProcessorClient;
import br.com.pvss.rinhabackend2025.dto.PaymentRequestDto;
import br.com.pvss.rinhabackend2025.dto.ProcessorType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
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
        String correlationId = request.correlationId().toString();

        return redisSummaryService.isAlreadyProcessed(correlationId)
                .flatMap(alreadyProcessed -> {
                    if (Boolean.TRUE.equals(alreadyProcessed)) {
                        log.info("Pagamento já processado (idempotente): {}", correlationId);
                        return Mono.empty();
                    }
                    return processPaymentWithFallback(request)
                            .then(redisSummaryService.markAsProcessed(correlationId, request.amount()));
                });
    }

    private Mono<Void> processPaymentWithFallback(PaymentRequestDto request) {
        return healthCheckService.getAvailableProcessor()
                .flatMap(primaryProcessor -> attemptPayment(primaryProcessor, request)
                        .onErrorResume(primaryError -> {
                            ProcessorType fallbackProcessor = (primaryProcessor == ProcessorType.DEFAULT)
                                    ? ProcessorType.FALLBACK : ProcessorType.DEFAULT;

                            log.warn("Tentativa primária com {} falhou. Tentando fallback: {}", primaryProcessor, fallbackProcessor);
                            return attemptPayment(fallbackProcessor, request);
                        }));
    }

    private Mono<Void> attemptPayment(ProcessorType processor, PaymentRequestDto request) {
        return paymentProcessorClient.sendPayment(processor, request)
                .retryWhen(Retry.backoff(2, Duration.ofMillis(100)).maxBackoff(Duration.ofSeconds(1))
                        .filter(this::isRetryableError)
                        .doBeforeRetry(retrySignal -> log.warn("Tentando novamente para {} - Tentativa #{}", processor, retrySignal.totalRetries() + 1)))
                .flatMap(usedProcessor ->
                        redisSummaryService.persistPaymentSummary(usedProcessor, request.amount(), request.correlationId()));
    }

    private boolean isRetryableError(Throwable error) {
        if (error instanceof java.util.concurrent.TimeoutException) return true;
        String message = error.getMessage().toLowerCase();
        return message.contains("timeout") || message.contains("connection reset") || message.contains("connection refused");
    }
}