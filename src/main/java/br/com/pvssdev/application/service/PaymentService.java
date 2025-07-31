package br.com.pvssdev.application.service;

import br.com.pvssdev.application.dto.PaymentRequestDto;
import br.com.pvssdev.application.dto.PaymentsSummaryDto;
import br.com.pvssdev.application.dto.ProcessorDetailDto;
import br.com.pvssdev.domain.model.Payment;
import br.com.pvssdev.domain.model.ProcessorType;
import br.com.pvssdev.infrastructure.persistence.PanachePaymentRepository;
import br.com.pvssdev.infrastructure.persistence.SummaryQueryDto;
import io.quarkus.hibernate.reactive.panache.common.WithTransaction;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.stream.Collectors;

@ApplicationScoped
public class PaymentService {

    @Inject
    PanachePaymentRepository paymentRepository;

    @WithTransaction
    public Uni<Void> processPayment(PaymentRequestDto requestDto) {
        Payment newPayment = new Payment(requestDto);
        return paymentRepository.save(newPayment);
    }

    @WithTransaction
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