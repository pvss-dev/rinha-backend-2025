package br.com.pvssdev.application.service;

import br.com.pvssdev.domain.model.Payment;
import br.com.pvssdev.domain.model.PaymentStatus;
import br.com.pvssdev.domain.model.ProcessorType;
import br.com.pvssdev.infrastructure.client.DefaultPaymentProcessorClient;
import br.com.pvssdev.infrastructure.client.FallbackPaymentProcessorClient;
import br.com.pvssdev.infrastructure.client.dto.HealthStatus;
import br.com.pvssdev.infrastructure.client.dto.ProcessorRequest;
import br.com.pvssdev.infrastructure.persistence.PanachePaymentRepository;
import br.com.pvssdev.infrastructure.scheduler.ProcessorHealthCache;
import io.quarkus.hibernate.reactive.panache.common.WithTransaction;
import io.quarkus.logging.Log;
import io.quarkus.scheduler.Scheduled;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.rest.client.inject.RestClient;

@ApplicationScoped
public class PaymentProcessorWorker {

    @Inject
    PanachePaymentRepository paymentRepository;

    @Inject
    @RestClient
    DefaultPaymentProcessorClient defaultProcessor;

    @Inject
    @RestClient
    FallbackPaymentProcessorClient fallbackProcessor;

    @Inject
    ProcessorHealthCache healthCache;

    @ConfigProperty(name = "processor.batch.size")
    int batchSize;

    @WithTransaction
    @Scheduled(every = "{processor.schedule.every}", concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    public Uni<Void> processPendingPayments() {
        return paymentRepository.findAndLockPendingPayments(batchSize)
                .onItem().ifNotNull().transformToMulti(payments -> Multi.createFrom().iterable(payments))
                .onItem().transformToUniAndMerge(this::processSinglePaymentWithHealthCheck)
                .collect().asList()
                .replaceWithVoid()
                .onFailure().invoke(failure -> Log.error("Error processing payment batch", failure));
    }

    private Uni<Void> processSinglePaymentWithHealthCheck(Payment payment) {
        ProcessorRequest processorRequest = new ProcessorRequest(payment.correlationId, payment.amount, payment.createdAt);

        HealthStatus defaultStatus = healthCache.getDefaultStatus();

        if (!defaultStatus.failing()) {
            return defaultProcessor.process(processorRequest)
                    .onItem().transformToUni(response -> {
                        Log.infof("Payment %s processed by DEFAULT.", payment.correlationId);
                        return paymentRepository.updatePaymentStatus(payment.id, ProcessorType.DEFAULT, PaymentStatus.PROCESSED_DEFAULT)
                                .replaceWithVoid();
                    })
                    .onFailure().recoverWithUni(failure -> {
                        Log.warnf(failure, "Failed on DEFAULT for payment %s despite health check. Trying FALLBACK.", payment.correlationId);
                        return executeFallback(payment, processorRequest);
                    });
        }

        Log.warnf("DEFAULT processor is failing according to health check. Directly trying FALLBACK for payment %s.", payment.correlationId);
        return executeFallback(payment, processorRequest);
    }

    private Uni<Void> executeFallback(Payment payment, ProcessorRequest processorRequest) {
        HealthStatus fallbackStatus = healthCache.getFallbackStatus();

        if (fallbackStatus.failing()) {
            Log.errorf("FATAL: Both processors are failing. Marking payment %s as FAILED without attempting call.", payment.correlationId);
            return paymentRepository.updatePaymentAsFailed(payment.id).replaceWithVoid();
        }

        return fallbackProcessor.process(processorRequest)
                .onItem().transformToUni(response -> {
                    Log.infof("Payment %s processed by FALLBACK.", payment.correlationId);
                    return paymentRepository.updatePaymentStatus(payment.id, ProcessorType.FALLBACK, PaymentStatus.PROCESSED_FALLBACK)
                            .replaceWithVoid();
                })
                .onFailure().recoverWithUni(finalFailure -> {
                    Log.errorf(finalFailure, "FATAL: Payment %s failed on FALLBACK as well. Marking as FAILED.", payment.correlationId);
                    return paymentRepository.updatePaymentAsFailed(payment.id)
                            .replaceWithVoid();
                });
    }
}