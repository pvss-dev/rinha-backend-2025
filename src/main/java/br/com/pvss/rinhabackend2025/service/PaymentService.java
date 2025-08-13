// src/main/java/br/com/pvss/rinhabackend2025/service/PaymentService.java
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
import java.time.temporal.ChronoUnit;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@Service
public class PaymentService implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

    private final BlockingQueue<ProcessorPaymentRequest> paymentQueue;
    private final PaymentProcessorClient client;
    private final MongoSummaryService summary;
    private final HealthCheckService healthCheckService;
    private final int workerThreads;
    private final int paymentTimeoutMs;
    private final int enqueueMaxWaitMs;

    public PaymentService(
            PaymentProcessorClient client,
            MongoSummaryService summary,
            HealthCheckService healthCheckService,
            BlockingQueue<ProcessorPaymentRequest> paymentQueue,
            @Value("${worker.threads}") int workerThreads,
            @Value("${payment.timeout.ms}") int paymentTimeoutMs,
            @Value("${enqueue.maxWait.ms}") int enqueueMaxWaitMs
    ) {
        this.client = client;
        this.summary = summary;
        this.healthCheckService = healthCheckService;
        this.paymentQueue = paymentQueue;
        this.workerThreads = workerThreads;
        this.paymentTimeoutMs = paymentTimeoutMs;
        this.enqueueMaxWaitMs = enqueueMaxWaitMs;
    }

    @Override
    public void run(String... args) {
        for (int i = 0; i < workerThreads; i++) {
            Executors.newVirtualThreadPerTaskExecutor().execute(this::runWorker);
        }
    }

    public boolean enqueue(PaymentRequestDto request) {
        var normalized = request.amount().setScale(2, RoundingMode.HALF_EVEN);
        var now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        var payload = new ProcessorPaymentRequest(request.correlationId(), normalized, now);
        try {
            return paymentQueue.offer(payload, enqueueMaxWaitMs, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private void runWorker() {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                ProcessorPaymentRequest req = paymentQueue.take();
                processPayment(req);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.error("Worker thread interrupted", e);
            }
        }
    }

    private void processPayment(ProcessorPaymentRequest payload) {
        ProcessorType first = healthCheckService.getAvailableProcessor();
        if (first == null) {
            try {
                paymentQueue.put(payload);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return;
        }

        ProcessorType second = (first == ProcessorType.DEFAULT) ? ProcessorType.FALLBACK : ProcessorType.DEFAULT;

        SendResult result = trySendPaymentWithRetries(first, payload);

        if (result == SendResult.SUCCESS || result == SendResult.DUPLICATE) {
            summary.persistPayment(first, payload);
            return;
        }

        result = trySendPaymentWithRetries(second, payload);

        if (result == SendResult.SUCCESS || result == SendResult.DUPLICATE) {
            summary.persistPayment(second, payload);
            return;
        }

        log.error("Falha total ao processar pagamento {}. Reenfileirando para nova tentativa.", payload.correlationId());
        try {
            paymentQueue.put(payload);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private SendResult trySendPaymentWithRetries(ProcessorType processor, ProcessorPaymentRequest payload) {
        for (int i = 0; i < 2; i++) {
            SendResult result = client.sendPayment(processor, payload);
            if (result == SendResult.SUCCESS || result == SendResult.DUPLICATE) {
                return result;
            }
            if (result == SendResult.RETRIABLE_FAILURE) {
                int backoff = Math.min(200, Math.max(50, paymentTimeoutMs / 2));
                try {
                    Thread.sleep(backoff);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return SendResult.RETRIABLE_FAILURE;
                }
            }
            if (client.wasProcessed(processor, payload.correlationId())) {
                return SendResult.SUCCESS;
            }
        }
        return SendResult.RETRIABLE_FAILURE;
    }
}