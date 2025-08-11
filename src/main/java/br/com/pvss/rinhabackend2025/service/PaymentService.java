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

        public PaymentService(PaymentProcessorClient client,
                              MongoSummaryService summary,
                              HealthCheckService healthCheckService,
                              BlockingQueue<ProcessorPaymentRequest> paymentQueue,
                              @Value("${worker.threads:20}") int workerThreads) {
            this.client = client;
            this.summary = summary;
            this.healthCheckService = healthCheckService;
            this.paymentQueue = paymentQueue;
            this.workerThreads = workerThreads;
        }

        public boolean enqueue(PaymentRequestDto request) {
            var normalized = request.amount().setScale(2, RoundingMode.HALF_EVEN);
            var payload = new ProcessorPaymentRequest(request.correlationId(), normalized, Instant.now());
            return paymentQueue.offer(payload);
        }

        private void runWorker() {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    ProcessorPaymentRequest request = paymentQueue.take();
                    processPayment(request);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    log.error("Worker thread interrupted", e);
                }
            }
        }

        private void processPayment(ProcessorPaymentRequest payload) {
            ProcessorType first = healthCheckService.getAvailableProcessor();
            if (first == null) {
                paymentQueue.offer(payload);
                return;
            }
            ProcessorType second = (first == ProcessorType.DEFAULT) ? ProcessorType.FALLBACK : ProcessorType.DEFAULT;

            for (ProcessorType p : new ProcessorType[]{first, second}) {
                for (int i = 0; i < 2; i++) {
                    SendResult r = client.sendPayment(p, payload);
                    if (r == SendResult.SUCCESS) {
                        summary.persistPayment(p, payload);
                        return;
                    }
                    if (r == SendResult.DUPLICATE) {
                        return;
                    }
                }
            }
        }

        @Override
        public void run(String... args) {
            for (int i = 0; i < workerThreads; i++) {
                Thread.ofVirtual().name("payment-worker-" + i).start(this::runWorker);
            }
        }
    }