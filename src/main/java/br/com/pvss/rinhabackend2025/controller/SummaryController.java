package br.com.pvss.rinhabackend2025.controller;

import br.com.pvss.rinhabackend2025.service.RedisSummaryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

@RestController
public class SummaryController {

    private static final Logger log = LoggerFactory.getLogger(SummaryController.class);
    private static final DateTimeFormatter ISO_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
    private final RedisSummaryService redisSummaryService;

    public SummaryController(RedisSummaryService redisSummaryService) {
        this.redisSummaryService = redisSummaryService;
    }

    @GetMapping("/payments-summary")
    public Mono<Map<String, Map<String, Object>>> getSummary(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            LocalDateTime from,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            LocalDateTime to
    ) {

        log.debug("Requisição de summary - from: {}, to: {}", from, to);

        return Mono.zip(
                redisSummaryService.getSummary("default", from, to),
                redisSummaryService.getSummary("fallback", from, to)
        ).map(tuple -> {
            Map<String, Object> defaultSummary = createSummaryResponse(tuple.getT1());
            Map<String, Object> fallbackSummary = createSummaryResponse(tuple.getT2());

            log.debug("Summary response - default: {}, fallback: {}", defaultSummary, fallbackSummary);

            return Map.of(
                    "default", defaultSummary,
                    "fallback", fallbackSummary
            );
        });
    }

    private Map<String, Object> createSummaryResponse(Map<Object, Object> data) {
        long totalRequests = parseLong(data.get("totalRequests"));
        BigDecimal totalAmount = parseBigDecimal(data.get("totalAmount"));

        return Map.of(
                "totalRequests", totalRequests,
                "totalAmount", totalAmount
        );
    }

    private long parseLong(Object value) {
        if (value == null) return 0L;
        if (value instanceof Number num) return num.longValue();
        try {
            return Long.parseLong(value.toString());
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    private BigDecimal parseBigDecimal(Object value) {
        if (value == null) return BigDecimal.ZERO;
        if (value instanceof BigDecimal bd) return bd;
        if (value instanceof Number num) return new BigDecimal(num.toString());
        try {
            return new BigDecimal(value.toString());
        } catch (NumberFormatException e) {
            return BigDecimal.ZERO;
        }
    }
}