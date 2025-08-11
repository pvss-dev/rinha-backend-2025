package br.com.pvss.rinhabackend2025.service;

import br.com.pvss.rinhabackend2025.client.PaymentProcessorClient;
import br.com.pvss.rinhabackend2025.dto.ProcessorPaymentRequest;
import br.com.pvss.rinhabackend2025.dto.ProcessorType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class PagamentoWorker {

    private final PaymentProcessorClient client;
    private final MongoSummaryService summaryService;
    private final PagamentoQueue queue;

    public PagamentoWorker(PaymentProcessorClient client, MongoSummaryService summaryService, PagamentoQueue queue, @Value("${worker.threads:20}") int workerThreads) {
        this.client = client;
        this.summaryService = summaryService;
        this.queue = queue;

        for (int i = 0; i < workerThreads; i++) {
            Thread.ofVirtual().name("payment-worker-" + i).start(this::runWorker);
        }
    }

    private void runWorker() {
        while (true) {
            try {
                ProcessorPaymentRequest request = queue.take();
                processPayment(request);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    private void processPayment(ProcessorPaymentRequest payload) {
        boolean sucesso = false;
        // Tentativas para o processador default
        for (int i = 0; i < 15; i++) {
            try {
                if (client.sendPayment(ProcessorType.DEFAULT, payload)) {
                    summaryService.persistPayment(ProcessorType.DEFAULT, payload);
                    sucesso = true;
                    break;
                }
            } catch (Exception e) {
                // Ignore errors and retry
            }
        }

        if (!sucesso) {
            // Tenta o fallback
            try {
                if (client.sendPayment(ProcessorType.FALLBACK, payload)) {
                    summaryService.persistPayment(ProcessorType.FALLBACK, payload);
                    sucesso = true;
                }
            } catch (Exception e) {
                // Ignore error on fallback
            }
        }

        if (!sucesso) {
            // Handle final failure here if needed
        }
    }
}