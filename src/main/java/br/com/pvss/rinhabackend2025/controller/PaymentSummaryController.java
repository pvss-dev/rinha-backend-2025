package br.com.pvss.rinhabackend2025.controller;

import br.com.pvss.rinhabackend2025.dto.PaymentSummaryResponse;
import br.com.pvss.rinhabackend2025.service.PaymentSummaryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

@RestController
@RequestMapping("/payments-summary")
public class PaymentSummaryController {

    private final PaymentSummaryService summary;

    public PaymentSummaryController(PaymentSummaryService summary) {
        this.summary = summary;
    }

    @GetMapping
    public PaymentSummaryResponse getSummary(
            @RequestParam(value = "from", required = false) Instant from,
            @RequestParam(value = "to", required = false) Instant to
    ) {
        Instant effectiveFrom = (from != null) ? from : Instant.EPOCH;
        Instant effectiveTo = (to != null) ? to : Instant.now();
        return summary.summary(effectiveFrom, effectiveTo);
    }
}