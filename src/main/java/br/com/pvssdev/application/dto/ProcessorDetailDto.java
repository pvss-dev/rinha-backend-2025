package br.com.pvssdev.application.dto;

import java.math.BigDecimal;

public record ProcessorDetailDto(
        long totalRequests,
        BigDecimal totalAmount
) {
}
