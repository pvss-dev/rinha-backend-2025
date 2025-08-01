package br.com.pvssdev.application.dto;

import io.quarkus.runtime.annotations.RegisterForReflection;

import java.math.BigDecimal;

@RegisterForReflection
public record ProcessorDetailDto(
        long totalRequests,
        BigDecimal totalAmount
) {
}
