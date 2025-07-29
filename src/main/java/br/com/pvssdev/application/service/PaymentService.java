package br.com.pvssdev.application.service;

import br.com.pvssdev.application.dto.PaymentRequestDto;
import br.com.pvssdev.application.dto.PaymentsSummaryDto;
import br.com.pvssdev.application.dto.ProcessorDetailDto;
import br.com.pvssdev.domain.model.Payment;
import br.com.pvssdev.domain.model.ProcessorType;
import br.com.pvssdev.domain.repository.PaymentRepository;
import br.com.pvssdev.infrastructure.client.DefaultPaymentProcessorClient;
import br.com.pvssdev.infrastructure.client.FallbackPaymentProcessorClient;
import br.com.pvssdev.infrastructure.client.dto.HealthStatus;
import br.com.pvssdev.infrastructure.client.dto.ProcessorRequest;
import br.com.pvssdev.infrastructure.persistence.SummaryQueryDto;
import br.com.pvssdev.infrastructure.scheduler.ProcessorHealthCache;
import io.quarkus.logging.Log;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.stream.Collectors;

import org.eclipse.microprofile.rest.client.inject.RestClient;


@ApplicationScoped
public class PaymentService {
    @Inject
    ProcessorHealthCache healthCache;

    @Inject
    PaymentRepository paymentRepository;

    @Inject
    @RestClient
    DefaultPaymentProcessorClient defaultProcessor;

    @Inject
    @RestClient
    FallbackPaymentProcessorClient fallbackProcessor;

    @Transactional
    public Uni<Void> processPayment(PaymentRequestDto requestDto) {
        final var processorRequest = new ProcessorRequest(
                requestDto.correlationId(),
                requestDto.amount(),
                Instant.now()
        );

        HealthStatus defaultStatus = healthCache.getDefaultStatus();
        boolean tryDefaultFirst = (defaultStatus == null || !defaultStatus.failing());

        if (tryDefaultFirst) {
            return defaultProcessor.process(processorRequest)
                    .onItem().transformToUni(response -> {
                        Log.infof("Payment %s processed successfully by DEFAULT", requestDto.correlationId());
                        return paymentRepository.save(new Payment(requestDto, ProcessorType.DEFAULT));
                    })
                    .onFailure().recoverWithUni(failure -> {
                        Log.warnf(failure, "Failed to process payment %s with DEFAULT. Attempting FALLBACK.", requestDto.correlationId());
                        return executeFallback(processorRequest, requestDto);
                    });
        } else {
            Log.warnf("DEFAULT processor is offline. Routing payment %s directly to FALLBACK.", requestDto.correlationId());
            return executeFallback(processorRequest, requestDto);
        }
    }

    private Uni<Void> executeFallback(ProcessorRequest processorRequest, PaymentRequestDto requestDto) {
        return fallbackProcessor.process(processorRequest)
                .onItem().transformToUni(response -> {
                    Log.infof("Payment %s processed successfully by FALLBACK", requestDto.correlationId());
                    return paymentRepository.save(new Payment(requestDto, ProcessorType.FALLBACK));
                })
                .onFailure().invoke(finalFailure -> Log.errorf(finalFailure, "FATAL: Payment %s failed on FALLBACK as well.", requestDto.correlationId()));
    }

    public Uni<PaymentsSummaryDto> getSummary(Instant from, Instant to) {
        return paymentRepository.getSummary(from, to).map(results -> {
            Map<ProcessorType, ProcessorDetailDto> summaryMap = results.stream()
                    .collect(Collectors.toMap(
                            SummaryQueryDto::processor,
                            r -> new ProcessorDetailDto(r.totalRequests(), r.totalAmount())
                    ));
            var defaultSummary = summaryMap.getOrDefault(ProcessorType.DEFAULT, new ProcessorDetailDto(0L, BigDecimal.ZERO));
            var fallbackSummary = summaryMap.getOrDefault(ProcessorType.FALLBACK, new ProcessorDetailDto(0L, BigDecimal.ZERO));

            return new PaymentsSummaryDto(defaultSummary, fallbackSummary);
        });
    }
}