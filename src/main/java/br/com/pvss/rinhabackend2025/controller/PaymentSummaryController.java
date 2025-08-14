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

    private final PaymentSummaryService pagamentoSummaryService;

    public PaymentSummaryController(PaymentSummaryService pagamentoSummaryService) {
        this.pagamentoSummaryService = pagamentoSummaryService;
    }

    @GetMapping
    public PaymentSummaryResponse summary(
            @RequestParam(value = "from", required = false)
            Instant from,
            @RequestParam(value = "to", required = false)
            Instant to
    ) {
        return pagamentoSummaryService.getSummary(from, to);
    }
}