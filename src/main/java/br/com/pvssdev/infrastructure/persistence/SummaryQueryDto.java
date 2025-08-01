package br.com.pvssdev.infrastructure.persistence;

import br.com.pvssdev.domain.model.ProcessorType;
import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@AllArgsConstructor
@RegisterForReflection
public class SummaryQueryDto {
    private final ProcessorType processor;
    private final Long totalRequests;
    private final BigDecimal totalAmount;
}