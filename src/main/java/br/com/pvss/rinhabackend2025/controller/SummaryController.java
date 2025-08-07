package br.com.pvss.rinhabackend2025.controller;

import br.com.pvss.rinhabackend2025.service.RedisSummaryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.Map;

@RestController
public class SummaryController {

    private static final Logger log = LoggerFactory.getLogger(SummaryController.class);
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

        Mono<Map<String, Object>> defaultSummaryMono = redisSummaryService.getSummary("default", from, to);
        Mono<Map<String, Object>> fallbackSummaryMono = redisSummaryService.getSummary("fallback", from, to);

        return Mono.zip(defaultSummaryMono, fallbackSummaryMono)
                .map(tuple -> {
                    Map<String, Object> defaultSummary = tuple.getT1();
                    Map<String, Object> fallbackSummary = tuple.getT2();

                    log.debug("Summary response - default: {}, fallback: {}", defaultSummary, fallbackSummary);

                    return Map.of(
                            "default", defaultSummary,
                            "fallback", fallbackSummary
                    );
                });
    }
}