package br.com.pvss.rinhabackend2025.service;

import br.com.pvss.rinhabackend2025.dto.ProcessorType;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Map;

@Service
public class RedisSummaryService {

    private final ReactiveStringRedisTemplate redis;

    public RedisSummaryService(ReactiveStringRedisTemplate redis) {
        this.redis = redis;
    }

    public Mono<Boolean> isAlreadyProcessed(String id) {
        return redis.hasKey("correlation:" + id);
    }

    public Mono<Void> markAsProcessed(String id) {
        return redis.opsForValue().set("correlation:" + id, "1", Duration.ofHours(1)).then();
    }

    public Mono<Void> persistPaymentSummary(ProcessorType processor, BigDecimal amount) {
        String key = "summary:" + processor.name().toLowerCase();
        return redis.opsForHash().increment(key, "totalAmount", amount.doubleValue())
                .then(redis.opsForHash().increment(key, "totalRequests", 1))
                .then();
    }

    public Mono<Map<Object, Object>> getSummary(String processor) {
        return redis.opsForHash().entries("summary:" + processor).collectMap(Map.Entry::getKey, Map.Entry::getValue);
    }
}
