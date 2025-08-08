package br.com.pvss.rinhabackend2025.service;

import br.com.pvss.rinhabackend2025.dto.ProcessorType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class RedisSummaryService {

    private static final Logger log = LoggerFactory.getLogger(RedisSummaryService.class);
    private static final String CORRELATION_PREFIX = "processed:";
    private static final String PAYMENT_SORTED_SET_PREFIX = "payments:";
    private static final Duration CORRELATION_TTL = Duration.ofMinutes(10);

    private final ReactiveStringRedisTemplate redis;
    private final RedisScript<Long> persistScript;

    public RedisSummaryService(ReactiveStringRedisTemplate redis) {
        this.redis = redis;
        this.persistScript = new DefaultRedisScript<>(
                """
                        -- Check for idempotency
                        if redis.call('GET', KEYS[1]) then
                            return 0
                        end
                        -- Persist payment and set idempotency key
                        redis.call('ZADD', KEYS[2], ARGV[1], ARGV[2])
                        redis.call('SET', KEYS[1], ARGV[3], 'EX', ARGV[4])
                        return 1
                        """, Long.class
        );
    }

    public Mono<Void> persistPaymentSummary(ProcessorType processor, BigDecimal amount, UUID correlationId, Instant requestedAt) {
        String processorKey = processor.name().toLowerCase();
        String correlationKey = CORRELATION_PREFIX + correlationId;
        String sortedSetKey = PAYMENT_SORTED_SET_PREFIX + processorKey;

        double score = (double) requestedAt.toEpochMilli();
        String member = amount.toPlainString() + ":" + correlationId;

        List<String> keys = List.of(correlationKey, sortedSetKey);
        return redis.execute(this.persistScript, keys,
                        String.valueOf(score), member,
                        amount.toPlainString(),
                        String.valueOf(CORRELATION_TTL.toSeconds()))
                .then();
    }

    public Mono<Map<String, Object>> getSummary(String processor, LocalDateTime from, LocalDateTime to) {
        String sortedSetKey = PAYMENT_SORTED_SET_PREFIX + processor;

        double fromScore = (from != null) ? (double) from.toInstant(ZoneOffset.UTC).toEpochMilli() : Double.NEGATIVE_INFINITY;
        double toScore = (to != null) ? (double) to.toInstant(ZoneOffset.UTC).toEpochMilli() : Double.POSITIVE_INFINITY;

        return redis.opsForZSet().rangeByScore(sortedSetKey, Range.closed(fromScore, toScore))
                .map(member -> {
                    try {
                        String[] parts = member.split(":");
                        return new BigDecimal(parts[0]);
                    } catch (Exception e) {
                        log.warn("Membro do ZSET malformado: {}", member);
                        return BigDecimal.ZERO;
                    }
                })
                .reduce(new PaymentSummary(), (summary, amount) -> {
                    summary.totalAmount = summary.totalAmount.add(amount);
                    summary.totalRequests++;
                    return summary;
                })
                .map(summary -> Map.of(
                        "totalRequests", summary.totalRequests,
                        "totalAmount", summary.totalAmount
                ));
    }

    private static class PaymentSummary {
        BigDecimal totalAmount = BigDecimal.ZERO;
        long totalRequests = 0L;
    }
}