package br.com.pvss.rinhabackend2025.controller;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/payments-summary")
public class SummaryController {

    private final StringRedisTemplate redisTemplate;

    public SummaryController(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @GetMapping
    public Map<String, Object> summary(
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to
    ) {
        long fromTimestamp = (from != null) ? Instant.parse(from).toEpochMilli() : 0;
        long toTimestamp = (to != null) ? Instant.parse(to).toEpochMilli() : Long.MAX_VALUE;

        Map<String, Object> defaultSummary = getSummaryForProcessor("default", fromTimestamp, toTimestamp);
        Map<String, Object> fallbackSummary = getSummaryForProcessor("fallback", fromTimestamp, toTimestamp);

        return Map.of(
                "default", defaultSummary,
                "fallback", fallbackSummary
        );
    }

    private Map<String, Object> getSummaryForProcessor(String processorType, long fromTimestamp, long toTimestamp) {
        String key = "rinha:payments:" + processorType;

        Set<String> payments = redisTemplate.opsForZSet().rangeByScore(key, fromTimestamp, toTimestamp);

        if (payments == null || payments.isEmpty()) {
            return Map.of("totalRequests", 0L, "totalAmount", BigDecimal.ZERO);
        }

        long totalRequests = payments.size();
        BigDecimal totalAmount = payments.stream()
                .map(payment -> new BigDecimal(payment.split(":")[1]))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return Map.of(
                "totalRequests", totalRequests,
                "totalAmount", totalAmount
        );
    }
}