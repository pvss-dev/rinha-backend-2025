package br.com.pvss.rinhabackend2025.service;

import br.com.pvss.rinhabackend2025.client.PaymentProcessorClient;
import br.com.pvss.rinhabackend2025.dto.PaymentRequestDto;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
public class PaymentService {

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
                    if (alreadyProcessed) return Mono.empty();

                    return healthCheckService.getAvailableProcessor()
                            .flatMap(processor -> paymentProcessorClient.sendPayment(processor, request))
                            .flatMap(processor -> redisSummaryService.persistPaymentSummary(processor, request.amount()))
                            .then(redisSummaryService.markAsProcessed(request.correlationId()));
                });
    }
}
