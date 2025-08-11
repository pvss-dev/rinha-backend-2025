package br.com.pvss.rinhabackend2025.service;

import br.com.pvss.rinhabackend2025.client.PaymentProcessorClient;
import br.com.pvss.rinhabackend2025.dto.PaymentRequestDto;
import br.com.pvss.rinhabackend2025.dto.ProcessorPaymentRequest;
import br.com.pvss.rinhabackend2025.dto.ProcessorType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Service;

import java.math.RoundingMode;
import java.time.Instant;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

@Service
public class PaymentService implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);
    private final BlockingQueue<ProcessorPaymentRequest> paymentQueue = new LinkedBlockingQueue<>();
    private final PaymentProcessorClient client;
    private final MongoSummaryService summary;
    private final HealthCheckService healthCheckService;
    private final int workerThreads;

    public PaymentService(
            PaymentProcessorClient client,
            MongoSummaryService summary,
            HealthCheckService healthCheckService,
            @Value("${worker.threads:20}") int workerThreads) {
        this.client = client;
        this.summary = summary;
        this.healthCheckService = healthCheckService;
        this.workerThreads = workerThreads;
    }

    public void addPaymentToQueue(PaymentRequestDto request) {
        var normalizedAmount = request.amount().setScale(2, RoundingMode.HALF_UP);
        Instant requestedAt = Instant.now();
        var payload = new ProcessorPaymentRequest(request.correlationId(), normalizedAmount, requestedAt);
        paymentQueue.offer(payload);
    }

    private void runWorker() {
        while (true) {
            try {
                ProcessorPaymentRequest request = paymentQueue.take();
                processPayment(request);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.error("Worker thread interrupted", e);
                return;
            }
        }
    }

    private void processPayment(ProcessorPaymentRequest payload) {
        ProcessorType chosenProcessor = healthCheckService.getAvailableProcessor();
        if (chosenProcessor == null) {
            log.error("Failed to process payment, no available processors: {}", payload.correlationId());
            return;
        }

        boolean success = false;
        for (int i = 0; i < 15; i++) {
            try {
                if (client.sendPayment(chosenProcessor, payload)) {
                    summary.persistPayment(chosenProcessor, payload);
                    success = true;
                    break;
                }
            } catch (Exception e) {
                log.warn("Tentativa {} de 15 falhou para o processador {}. ID: {}", i + 1, chosenProcessor, payload.correlationId());
            }
        }

        if (!success) {
            ProcessorType fallbackProcessor = (chosenProcessor == ProcessorType.DEFAULT) ? ProcessorType.FALLBACK : ProcessorType.DEFAULT;
            try {
                if (client.sendPayment(fallbackProcessor, payload)) {
                    summary.persistPayment(fallbackProcessor, payload);
                    success = true;
                }
            } catch (Exception e) {
                log.error("Falha no fallback para o processador {}. ID: {}", fallbackProcessor, payload.correlationId());
            }
        }

        if (!success) {
            log.error("Falha total no processamento do pagamento após todos os retries e fallbacks. ID: {}", payload.correlationId());
        }
    }

    @Override
    public void run(String... args) {
        for (int i = 0; i < workerThreads; i++) {
            Thread.ofVirtual().name("payment-worker-" + i).start(this::runWorker);
        }
    }
}