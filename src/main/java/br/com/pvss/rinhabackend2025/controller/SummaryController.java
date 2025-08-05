package br.com.pvss.rinhabackend2025.controller;

import br.com.pvss.rinhabackend2025.service.RedisSummaryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.util.Map;

@RestController
public class SummaryController {

    private final RedisSummaryService redisSummaryService;

    public SummaryController(RedisSummaryService redisSummaryService) {
        this.redisSummaryService = redisSummaryService;
    }

    @GetMapping("/payments-summary")
    public Mono<Map<String, Map<String, Object>>> getSummary() {
        return Mono.zip(
                redisSummaryService.getSummary("default"),
                redisSummaryService.getSummary("fallback")
        ).map(tuple -> Map.of(
                "default", mapWithDefaults(tuple.getT1()),
                "fallback", mapWithDefaults(tuple.getT2())
        ));
    }

    private Map<String, Object> mapWithDefaults(Map<Object, Object> data) {
        long totalRequests = Long.parseLong(data.getOrDefault("totalRequests", "0").toString());
        BigDecimal totalAmount = new BigDecimal(data.getOrDefault("totalAmount", "0").toString());

        return Map.of(
                "totalRequests", totalRequests,
                "totalAmount", totalAmount
        );
    }
}