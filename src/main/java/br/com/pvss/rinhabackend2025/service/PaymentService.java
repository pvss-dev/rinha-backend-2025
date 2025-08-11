package br.com.pvss.rinhabackend2025.service;

import br.com.pvss.rinhabackend2025.client.PaymentProcessorClient;
import br.com.pvss.rinhabackend2025.dto.PaymentRequestDto;
import br.com.pvss.rinhabackend2025.dto.ProcessorPaymentRequest;
import br.com.pvss.rinhabackend2025.dto.ProcessorType;
import com.fasterxml.jackson.core.JsonProcessingException;
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
    private final int workerThreads;

    public PaymentService(
            PaymentProcessorClient client,
            MongoSummaryService summary,
            @Value("${worker.threads:20}") int workerThreads) {
        this.client = client;
        this.summary = summary;
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
        boolean success = false;
        for (int i = 0; i < 15; i++) {
            try {
                if (client.sendPayment(ProcessorType.DEFAULT, payload)) {
                    summary.persistPayment(ProcessorType.DEFAULT, payload);
                    success = true;
                    break;
                }
            } catch (JsonProcessingException e) {
                log.error("Error serializing payload: {}", e.getMessage());
                return;
            }
        }

        if (!success) {
            try {
                if (client.sendPayment(ProcessorType.FALLBACK, payload)) {
                    summary.persistPayment(ProcessorType.FALLBACK, payload);
                    success = true;
                }
            } catch (JsonProcessingException e) {
                log.error("Error serializing payload for fallback: {}", e.getMessage());
            }
        }

        if (!success) {
            log.error("Failed to process payment after all retries and fallbacks: {}", payload);
        }
    }

    @Override
    public void run(String... args) {
        for (int i = 0; i < workerThreads; i++) {
            Thread.ofVirtual().name("payment-worker-" + i).start(this::runWorker);
        }
    }
}