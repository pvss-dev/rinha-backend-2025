package br.com.pvss.rinhabackend2025.service;

import br.com.pvss.rinhabackend2025.config.PaymentProcessorClient;
import br.com.pvss.rinhabackend2025.dto.PaymentProcessorRequest;
import br.com.pvss.rinhabackend2025.model.CompletePaymentProcessor;
import br.com.pvss.rinhabackend2025.model.PaymentDocument;
import br.com.pvss.rinhabackend2025.repository.PaymentRepository;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.concurrent.LinkedBlockingQueue;

@Service
public class PaymentProcessorService {

    private final PaymentRepository paymentRepository;

    private final PaymentProcessorClient defaultPaymentProcessor;

    private final PaymentProcessorClient fallbackPaymentProcessor;

    private final LinkedBlockingQueue<CompletePaymentProcessor> pendingPayments = new LinkedBlockingQueue<>();

    public PaymentProcessorService(
            PaymentRepository paymentRepository,
            @Qualifier("paymentProcessorDefaultClient")
            PaymentProcessorClient defaultPaymentProcessor,
            @Qualifier("paymentProcessorFallbackClient")
            PaymentProcessorClient fallbackPaymentProcessor
    ) {

        this.paymentRepository = paymentRepository;
        this.defaultPaymentProcessor = defaultPaymentProcessor;
        this.fallbackPaymentProcessor = fallbackPaymentProcessor;

        for (int i = 0; i < 20; i++) {
            Thread.startVirtualThread(this::runWorker);
        }
    }

    private void runWorker() {
        while (true) {
            var request = fetchPayment();
            processPayment(request);
        }
    }

    private void processPayment(CompletePaymentProcessor completePaymentProcessor) {
        boolean success = pay(completePaymentProcessor);
        if (success) {
            return;
        }
        addToQueue(completePaymentProcessor);
    }

    public CompletePaymentProcessor fetchPayment() {
        try {
            return pendingPayments.take();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    public void addToQueue(CompletePaymentProcessor completePaymentProcessor) {
        pendingPayments.offer(completePaymentProcessor);
    }

    public String convertObjectToJson(PaymentProcessorRequest request) {
        return """
                {
                  "correlationId": "%s",
                  "amount": %s,
                  "requestedAt": "%s"
                }
                """.formatted(
                escape(request.correlationId().toString()),
                request.amount().toPlainString(),
                request.requestedAt().toString()
        ).replace("\n", "").replace("  ", "");
    }

    private String escape(String value) {
        return value.replace("\"", "\\\"");
    }

    public boolean pay(CompletePaymentProcessor completePaymentProcessor) {
        try {

            boolean success;
            for (int attempt = 0; attempt < 15; attempt++) {

                success = sendRequest(completePaymentProcessor.paymentJson(), true);
                if (success) {
                    saveDocument(completePaymentProcessor.paymentProcessorRequest(), true);
                    return true;
                }
            }

            success = sendRequest(completePaymentProcessor.paymentJson(), false);
            if (success) {
                saveDocument(completePaymentProcessor.paymentProcessorRequest(), false);
                return true;
            }
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    public boolean sendRequest(String payment, boolean isDefaultProcessor) {
        try {
            if (isDefaultProcessor) {
                return defaultPaymentProcessor.processPayment(payment);
            } else {
                return fallbackPaymentProcessor.processPayment(payment);
            }
        } catch (Exception ex) {
            return false;
        }
    }

    public void saveDocument(PaymentProcessorRequest paymentProcessorRequest, boolean isDefault) {
        PaymentDocument document = new PaymentDocument();

        document.setCorrelationId(paymentProcessorRequest.correlationId());
        document.setAmount(paymentProcessorRequest.amount());
        document.setPaymentProcessorDefault(isDefault);
        document.setCreatedAt(paymentProcessorRequest.requestedAt());

        paymentRepository.save(document);
    }
}