package br.com.pvss.rinhabackend2025.controller;

import br.com.pvss.rinhabackend2025.dto.SummaryResponse;
import br.com.pvss.rinhabackend2025.service.MongoSummaryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.time.Instant;

@RestController
public class SummaryController {

    private final MongoSummaryService summary;

    public SummaryController(MongoSummaryService summary) {
        this.summary = summary;
    }

    @GetMapping("/payments-summary")
    public Mono<SummaryResponse> getSummary(
            @RequestParam(required = false)
            Instant from,
            @RequestParam(required = false)
            Instant to
    ) {
        return summary.summaryNullable(from, to);
    }
}