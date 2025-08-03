package br.com.pvss.rinhabackend2025.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/payments-summary")
@RequiredArgsConstructor
public class SummaryController {

    private final StringRedisTemplate redisTemplate;

    @GetMapping
    public Map<String, Object> summary(
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to
    ) {

        List<String> results = redisTemplate.opsForValue().multiGet(List.of(
                "rinha:summary:default:requests",
                "rinha:summary:default:amount",
                "rinha:summary:fallback:requests",
                "rinha:summary:fallback:amount"
        ));

        long defaultRequests = Optional.ofNullable(results.get(0)).map(Long::parseLong).orElse(0L);
        BigDecimal defaultAmount = Optional.ofNullable(results.get(1)).map(BigDecimal::new).orElse(BigDecimal.ZERO);
        long fallbackRequests = Optional.ofNullable(results.get(2)).map(Long::parseLong).orElse(0L);
        BigDecimal fallbackAmount = Optional.ofNullable(results.get(3)).map(BigDecimal::new).orElse(BigDecimal.ZERO);

        Map<String, Object> defaultSummary = Map.of(
                "totalRequests", defaultRequests,
                "totalAmount", defaultAmount
        );
        Map<String, Object> fallbackSummary = Map.of(
                "totalRequests", fallbackRequests,
                "totalAmount", fallbackAmount
        );

        return Map.of(
                "default", defaultSummary,
                "fallback", fallbackSummary
        );
    }
}