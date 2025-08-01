package br.com.pvssdev.infrastructure.persistence;

import br.com.pvssdev.domain.model.ProcessorType;

import java.math.BigDecimal;

public interface SummaryQueryDto {
    ProcessorType processor();

    long totalRequests();

    BigDecimal totalAmount();
}