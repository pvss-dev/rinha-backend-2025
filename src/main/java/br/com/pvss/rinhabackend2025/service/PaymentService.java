package br.com.pvss.rinhabackend2025.service;

import br.com.pvss.rinhabackend2025.client.PaymentProcessorClient;
import br.com.pvss.rinhabackend2025.dto.PaymentRequestDto;
import br.com.pvss.rinhabackend2025.dto.ProcessorPaymentRequest;
import br.com.pvss.rinhabackend2025.dto.ProcessorType;
import br.com.pvss.rinhabackend2025.dto.SendResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Service;

import java.math.RoundingMode;
import java.time.Instant;
import java.util.concurrent.BlockingQueue;

@Service
public class PaymentService implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);
    private final BlockingQueue<ProcessorPaymentRequest> paymentQueue;
    private final PaymentProcessorClient client;
    private final MongoSummaryService summary;
    private final HealthCheckService healthCheckService;
    private final int workerThreads;
    private final int paymentTimeoutMs;

    public PaymentService(
            PaymentProcessorClient client,
            MongoSummaryService summary,
            HealthCheckService healthCheckService,
            BlockingQueue<ProcessorPaymentRequest> paymentQueue,
            @Value("${worker.threads}") int workerThreads,
            @Value("${payment.timeout.ms}") int paymentTimeoutMs
    ) {
        this.client = client;
        this.summary = summary;
        this.healthCheckService = healthCheckService;
        this.paymentQueue = paymentQueue;
        this.workerThreads = workerThreads;
        this.paymentTimeoutMs = paymentTimeoutMs;
    }

    public boolean enqueue(PaymentRequestDto request) {
        var normalized = request.amount().setScale(2, RoundingMode.HALF_EVEN);
        var now = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.SECONDS);
        var payload = new ProcessorPaymentRequest(request.correlationId(), normalized, now);
        return paymentQueue.offer(payload);
    }

    private void runWorker() {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                ProcessorPaymentRequest req = paymentQueue.take();
                try {
                    processPayment(req);
                } catch (Exception ex) {
                    log.error("Erro processando {}, descartando", req.correlationId(), ex);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.error("Worker thread interrupted", e);
            }
        }
    }

    private void processPayment(ProcessorPaymentRequest payload) {
        ProcessorType first = healthCheckService.getAvailableProcessor();
        if (first == null) {
            boolean requeued = paymentQueue.offer(payload);
            if (!requeued) {
                log.warn("Re-enqueue falhou (fila cheia). Descartando pagamento {}", payload.correlationId());
            }
            return;
        }
        ProcessorType second = (first == ProcessorType.DEFAULT) ? ProcessorType.FALLBACK : ProcessorType.DEFAULT;

        for (ProcessorType p : new ProcessorType[]{first, second}) {
            for (int i = 0; i < 2; i++) {
                SendResult r = client.sendPayment(p, payload);

                if (r == SendResult.SUCCESS) {
                    summary.persistPayment(p, payload);
                    if (p == ProcessorType.FALLBACK && client.wasProcessed(ProcessorType.DEFAULT, payload.correlationId())) {
                        summary.persistPayment(ProcessorType.DEFAULT, payload);
                    }
                    return;
                }

                if (r == SendResult.DUPLICATE) {
                    summary.persistPayment(p, payload);
                    return;
                }

                if (r == SendResult.RETRIABLE_FAILURE && p == first) {
                    int backoff = Math.min(200, Math.max(50, paymentTimeoutMs / 2));
                    try {
                        Thread.sleep(backoff);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                    }
                    if (client.wasProcessed(first, payload.correlationId())) {
                        summary.persistPayment(first, payload);
                        return;
                    }
                }

                if (client.wasProcessed(p, payload.correlationId())) {
                    summary.persistPayment(p, payload);
                    return;
                }
            }
        }

        if (!paymentQueue.offer(payload)) {
            log.warn("Re-enqueue falhou (fila cheia). Descartando pagamento {}", payload.correlationId());
        }
    }

    @Override
    public void run(String... args) {
        for (int i = 0; i < workerThreads; i++) {
            Thread.ofVirtual().name("payment-worker-" + i).start(this::runWorker);
        }
    }
}