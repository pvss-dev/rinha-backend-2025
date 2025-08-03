package br.com.pvss.rinhabackend2025.controller;

import br.com.pvss.rinhabackend2025.entity.PaymentRequestEntity;
import br.com.pvss.rinhabackend2025.repository.PaymentRequestRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/payments-summary")
@RequiredArgsConstructor
public class SummaryController {

    private final PaymentRequestRepository repository;

    @GetMapping
    public Map<String, Object> summary(@RequestParam(required = false) Instant from, @RequestParam(required = false) Instant to) {
        List<Map<String, Object>> results = repository.getSummary(from, to, PaymentRequestEntity.PaymentStatus.SUCCESS);

        Map<String, Map<String, Object>> summaryMap = results.stream()
                .collect(Collectors.toMap(
                        result -> (String) result.get("processor"),
                        result -> Map.of(
                                "totalAmount", result.get("totalAmount"),
                                "totalRequests", result.get("totalRequests")
                        )
                ));

        Map<String, Object> defaultSummary = summaryMap.getOrDefault("http://payment-processor-default:8080",
                Map.of("totalRequests", 0L, "totalAmount", BigDecimal.ZERO));

        Map<String, Object> fallbackSummary = summaryMap.getOrDefault("http://payment-processor-fallback:8080",
                Map.of("totalRequests", 0L, "totalAmount", BigDecimal.ZERO));

        return Map.of(
                "default", defaultSummary,
                "fallback", fallbackSummary
        );
    }
}