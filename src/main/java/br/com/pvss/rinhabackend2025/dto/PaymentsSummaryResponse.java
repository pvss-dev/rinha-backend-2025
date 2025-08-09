package br.com.pvss.rinhabackend2025.dto;

import java.math.BigDecimal;

public record PaymentsSummaryResponse(
        BigDecimal totalAmount,
        int totalRequests,
        BigDecimal totalFee,
        BigDecimal feePerTransaction
) {
}