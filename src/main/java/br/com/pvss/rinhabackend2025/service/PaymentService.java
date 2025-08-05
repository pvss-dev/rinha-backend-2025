package br.com.pvss.rinhabackend2025.service;

import br.com.pvss.rinhabackend2025.client.PaymentProcessorClient;
import br.com.pvss.rinhabackend2025.dto.PaymentRequestDto;
import br.com.pvss.rinhabackend2025.dto.ProcessorType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

    private final HealthCheckService healthCheckService;
    private final PaymentProcessorClient paymentProcessorClient;
    private final RedisSummaryService redisSummaryService;

    public PaymentService(HealthCheckService healthCheckService, PaymentProcessorClient paymentProcessorClient, RedisSummaryService redisSummaryService) {
        this.healthCheckService = healthCheckService;
        this.paymentProcessorClient = paymentProcessorClient;
        this.redisSummaryService = redisSummaryService;
    }

    public Mono<Void> processPayment(PaymentRequestDto request) {
        return redisSummaryService.isAlreadyProcessed(request.correlationId())
                .flatMap(alreadyProcessed -> {
                    if (Boolean.TRUE.equals(alreadyProcessed)) {
                        log.debug("Requisição idempotente ignorada: {}", request.correlationId());
                        return Mono.empty();
                    }

                    return healthCheckService.getAvailableProcessor()
                            .flatMap(primary -> {
                                ProcessorType fallback = (primary == ProcessorType.DEFAULT) ? ProcessorType.FALLBACK : ProcessorType.DEFAULT;

                                return attemptPayment(primary, request)
                                        .onErrorResume(e -> {
                                            log.warn("Falha ao processar com {}. Tentando fallback com {}. Causa: {}", primary, fallback, e.getMessage());
                                            return attemptPayment(fallback, request);
                                        });
                            })
                            .then(redisSummaryService.markAsProcessed(request.correlationId()));
                });
    }

    private Mono<Void> attemptPayment(ProcessorType processor, PaymentRequestDto request) {
        return paymentProcessorClient.sendPayment(processor, request)
                .flatMap(type -> redisSummaryService.persistPaymentSummary(type, request.amount()));
    }
}