package br.com.pvss.rinhabackend2025.service;

import br.com.pvss.rinhabackend2025.client.PaymentProcessorClient;
import br.com.pvss.rinhabackend2025.dto.PaymentRequestDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

@Service
public class PaymentWorker {

    private static final Logger log = LoggerFactory.getLogger(PaymentWorker.class);

    private final HealthCheckService healthCheckService;
    private final PaymentProcessorClient processorClient;
    private final RedisSummaryService redisSummaryService;

    private final Queue<PaymentRequestDto> internalQueue = new ConcurrentLinkedQueue<>();

    public PaymentWorker(HealthCheckService healthCheckService, PaymentProcessorClient processorClient, RedisSummaryService redisSummaryService) {
        this.healthCheckService = healthCheckService;
        this.processorClient = processorClient;
        this.redisSummaryService = redisSummaryService;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void startProcessing() {
        Flux.interval(Duration.ofMillis(1))
                .publishOn(Schedulers.boundedElastic())
                .subscribe(tick -> {
                    PaymentRequestDto dto = internalQueue.poll();
                    if (dto != null) {
                        process(dto);
                    }
                });
    }

    private void process(PaymentRequestDto dto) {
        redisSummaryService.isAlreadyProcessed(dto.correlationId())
                .flatMap(already -> {
                    if (already) return null;
                    return healthCheckService.getAvailableProcessor()
                            .flatMap(type -> processorClient.sendPayment(type, dto)
                                    .then(redisSummaryService.persistPaymentSummary(type, dto.amount()))
                                    .then(redisSummaryService.markAsProcessed(dto.correlationId())));
                })
                .onErrorResume(e -> {
                    log.warn("Erro ao processar pagamento: {}", e.getMessage());
                    return null;
                })
                .subscribe();
    }
}
