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
import java.util.concurrent.LinkedBlockingQueue;

@Service
public class PaymentService implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

    private final BlockingQueue<ProcessorPaymentRequest> paymentQueue;
    private final PaymentProcessorClient client;
    private final MongoSummaryService summary;
    private final HealthCheckService healthCheckService;
    private final int workerThreads;
    private final int paymentTimeoutMs;
    private final BlockingQueue<PersistOp> persistQueue = new LinkedBlockingQueue<>(20000);
    private final int enqueueMaxWaitMs;

    private record PersistOp(ProcessorType p, ProcessorPaymentRequest payload) {
    }

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

    private static <T> void offerOrBlock(BlockingQueue<T> q, T item) {
        for (int i = 0; i < 64; i++) {
            if (q.offer(item)) return;
            Thread.onSpinWait();
        }
        try {
            q.put(item);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }

    private void safePersist(ProcessorType p, ProcessorPaymentRequest payload) {
        try {
            summary.persistPayment(p, payload);
        } catch (Exception e) {
            offerOrBlock(persistQueue, new PersistOp(p, payload));
        }
    }

    public boolean enqueue(PaymentRequestDto request) {
        var normalized = request.amount().setScale(2, RoundingMode.HALF_EVEN);
        var now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        var payload = new ProcessorPaymentRequest(request.correlationId(), normalized, now);
        try {
            return paymentQueue.offer(payload, enqueueMaxWaitMs, java.util.concurrent.TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private void runWorker() {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                ProcessorPaymentRequest req = paymentQueue.take();
                try {
                    processPayment(req);
                } catch (Exception ex) {
                    log.error("Erro processando {}, descartando do ciclo (evento será re-enfileirado se necessário)", req.correlationId(), ex);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.error("Worker thread interrupted", e);
            }
        }
    }

    private void runPersistWorker() {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                PersistOp op = persistQueue.take();
                int tries = 0;
                while (true) {
                    try {
                        summary.persistPayment(op.p(), op.payload());
                        break;
                    } catch (org.springframework.dao.DuplicateKeyException dk) {
                        break;
                    } catch (Exception ex) {
                        if (++tries >= 20) {
                            offerOrBlock(persistQueue, op);
                            try {
                                Thread.sleep(50);
                            } catch (InterruptedException ie) {
                                Thread.currentThread().interrupt();
                            }
                            break;
                        }
                        try {
                            Thread.sleep(25);
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                        }
                    }
                }
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private void processPayment(ProcessorPaymentRequest payload) {
        ProcessorType first = healthCheckService.getAvailableProcessor();
        if (first == null) {
            offerOrBlock(paymentQueue, payload);
            return;
        }

        ProcessorType second = (first == ProcessorType.DEFAULT) ? ProcessorType.FALLBACK : ProcessorType.DEFAULT;

        for (ProcessorType p : new ProcessorType[]{first, second}) {
            for (int i = 0; i < 2; i++) {
                SendResult r = client.sendPayment(p, payload);

                if (r == SendResult.SUCCESS) {
                    safePersist(p, payload);
                    if (p == ProcessorType.FALLBACK && client.wasProcessed(ProcessorType.DEFAULT, payload.correlationId())) {
                        safePersist(ProcessorType.DEFAULT, payload);
                    }
                    return;
                }

                if (r == SendResult.DUPLICATE) {
                    safePersist(p, payload);
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
                        safePersist(first, payload);
                        return;
                    }
                }

                if (client.wasProcessed(p, payload.correlationId())) {
                    safePersist(p, payload);
                    return;
                }
            }
        }

        offerOrBlock(paymentQueue, payload);
    }

    @Override
    public void run(String... args) {
        for (int i = 0; i < workerThreads; i++) {
            Thread.ofVirtual().name("payment-worker-" + i).start(this::runWorker);
        }
        for (int i = 0; i < 3; i++) {
            Thread.ofVirtual().name("persist-worker-" + i).start(this::runPersistWorker);
        }
    }
}