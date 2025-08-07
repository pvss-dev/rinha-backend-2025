package br.com.pvss.rinhabackend2025.worker;

import br.com.pvss.rinhabackend2025.dto.PaymentRequestDto;
import br.com.pvss.rinhabackend2025.service.PaymentQueueService;
import br.com.pvss.rinhabackend2025.service.PaymentService;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.UUID;

@Service
public class PaymentProcessorWorker {

    private static final Logger log = LoggerFactory.getLogger(PaymentProcessorWorker.class);
    private final PaymentQueueService paymentQueueService;
    private final PaymentService paymentService;

    public PaymentProcessorWorker(PaymentQueueService paymentQueueService, PaymentService paymentService) {
        this.paymentQueueService = paymentQueueService;
        this.paymentService = paymentService;
    }

    @PostConstruct
    public void startProcessing() {
        log.info("Iniciando o worker de processamento de pagamentos...");
        paymentQueueService.redis.opsForList()
                .rightPop(PaymentQueueService.PAYMENT_QUEUE_KEY, Duration.ofSeconds(5))
                .repeat()
                .parallel(100)
                .runOn(Schedulers.parallel())
                .flatMap(this::processPayload)
                .sequential()
                .subscribe(
                        null,
                        err -> log.error("Erro fatal no worker de pagamentos. Ele será reiniciado.", err),
                        () -> log.warn("Worker de pagamentos foi finalizado inesperadamente.")
                );
    }

    private Mono<Void> processPayload(String payload) {
        try {
            String[] parts = payload.split(":");
            PaymentRequestDto request = new PaymentRequestDto(UUID.fromString(parts[0]), new BigDecimal(parts[1]));

            return paymentService.processPayment(request)
                    .onErrorResume(e -> {
                        log.error("Falha persistente ao processar {}. Devolvendo para a fila.", request.correlationId(), e);
                        return paymentQueueService.enqueuePaymentAtHead(request).then();
                    });
        } catch (Exception e) {
            log.error("Payload com formato inválido descartado: '{}'", payload, e);
            return Mono.empty();
        }
    }
}