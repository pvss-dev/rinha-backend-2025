package br.com.pvss.rinhabackend2025.service;

import br.com.pvss.rinhabackend2025.config.PaymentProcessorClient;
import br.com.pvss.rinhabackend2025.dto.ProcessorPaymentRequest;
import br.com.pvss.rinhabackend2025.model.CompletePaymentProcessor;
import br.com.pvss.rinhabackend2025.model.PaymentDocument;
import br.com.pvss.rinhabackend2025.repository.PaymentRepository;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.concurrent.LinkedBlockingQueue;

@Service
public class PaymentProcessorService {

    private final PaymentRepository paymentRepository;

    private final PaymentProcessorClient paymentProcessorDefault;

    private final PaymentProcessorClient paymentProcessorFallback;

    private final LinkedBlockingQueue<CompletePaymentProcessor> paymentsPendentes = new LinkedBlockingQueue<>();

    public PaymentProcessorService(
            PaymentRepository paymentRepository,
            @Qualifier("paymentProcessorDefaultClient") PaymentProcessorClient paymentProcessorDefault,
            @Qualifier("paymentProcessorFallbackClient") PaymentProcessorClient paymentProcessorFallback
    ) {

        this.paymentRepository = paymentRepository;
        this.paymentProcessorDefault = paymentProcessorDefault;
        this.paymentProcessorFallback = paymentProcessorFallback;

        for (int i = 0; i < 20; i++) {
            Thread.startVirtualThread(this::runWorker);
        }
    }

    private void runWorker() {
        while (true) {
            var request = buscarPayment();
            processPayment(request);
        }
    }

    private void processPayment(CompletePaymentProcessor paymentProcessorCompleto) {
        boolean sucesso = pagar(paymentProcessorCompleto);
        if (sucesso) {
            return;
        }
        adicionaNaFila(paymentProcessorCompleto);
    }

    public CompletePaymentProcessor buscarPayment() {
        try {
            return paymentsPendentes.take();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    public void adicionaNaFila(CompletePaymentProcessor paymentProcessorCompleto) {
        paymentsPendentes.offer(paymentProcessorCompleto);
    }

    public String convertObjectToJson(ProcessorPaymentRequest request) {
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

    public boolean pagar(CompletePaymentProcessor paymentProcessorCompleto) {
        try {

            boolean sucesso;
            for (int tentativa = 0; tentativa < 15; tentativa++) {

                sucesso = enviarRequisicao(paymentProcessorCompleto.paymentJson(), true);
                if (sucesso) {
                    salvarDocument(paymentProcessorCompleto.request(), true);
                    return true;
                }
            }

            sucesso = enviarRequisicao(paymentProcessorCompleto.paymentJson(), false);
            if (sucesso) {
                salvarDocument(paymentProcessorCompleto.request(), false);
                return true;
            }
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    public boolean enviarRequisicao(String payment, boolean processadorDefault) {

        try {
            if (processadorDefault) {
                return paymentProcessorDefault.processPayment(payment);
            } else {
                return paymentProcessorFallback.processPayment(payment);
            }
        } catch (Exception ex) {
            return false;
        }
    }

    public void salvarDocument(ProcessorPaymentRequest paymentProcessorRequest, boolean isDefault) {
        PaymentDocument doc = new PaymentDocument();

        doc.setCorrelationId(paymentProcessorRequest.correlationId().toString());
        doc.setAmount(paymentProcessorRequest.amount());
        doc.setPaymentProcessorDefault(isDefault);
        doc.setCreatedAt(paymentProcessorRequest.requestedAt());

        paymentRepository.save(doc);
    }
}