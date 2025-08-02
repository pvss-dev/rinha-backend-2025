package br.com.pvss.rinhabackend2025.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/payments-summary")
@RequiredArgsConstructor
public class SummaryController {

    private final RedisTemplate<String, String> redisTemplate;

    @GetMapping
    public Map<String, Object> summary() {
        Long requests = Optional.ofNullable(redisTemplate.opsForValue().get("summary:requests"))
                .map(Long::valueOf).orElse(0L);
        Long amount = Optional.ofNullable(redisTemplate.opsForValue().get("summary:amount"))
                .map(Long::valueOf).orElse(0L);
        return Map.of(
                "totalRequests", requests,
                "totalAmount", BigDecimal.valueOf(amount)
        );
    }
}