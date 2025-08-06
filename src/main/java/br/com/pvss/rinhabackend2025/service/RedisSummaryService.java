package br.com.pvss.rinhabackend2025.service;

import br.com.pvss.rinhabackend2025.dto.ProcessorType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;

@Service
public class RedisSummaryService {

    private static final Logger log = LoggerFactory.getLogger(RedisSummaryService.class);
    private static final String CORRELATION_PREFIX = "processed:";
    private static final String SUMMARY_PREFIX = "summary:";
    private static final String PAYMENT_SORTED_SET_PREFIX = "payments:";
    private static final Duration CORRELATION_TTL = Duration.ofHours(2);

    private final ReactiveStringRedisTemplate redis;

    public RedisSummaryService(ReactiveStringRedisTemplate redis) {
        this.redis = redis;
    }

    public Mono<Boolean> isAlreadyProcessed(String correlationId) {
        return redis.hasKey(CORRELATION_PREFIX + correlationId);
    }

    public Mono<Void> markAsProcessed(String correlationId, BigDecimal amount) {
        return redis.opsForValue()
                .set(CORRELATION_PREFIX + correlationId, amount.toPlainString(), CORRELATION_TTL)
                .then();
    }

    public Mono<Void> persistPaymentSummary(ProcessorType processor, BigDecimal amount, UUID correlationId) {
        String processorKey = processor.name().toLowerCase();
        String summaryKey = SUMMARY_PREFIX + processorKey;
        String sortedSetKey = PAYMENT_SORTED_SET_PREFIX + processorKey;

        double score = LocalDateTime.now().toInstant(ZoneOffset.UTC).toEpochMilli() * 1000.0 + (System.nanoTime() % 1000) / 1000.0;
        String member = amount.toPlainString() + ":" + correlationId.toString();

        return Mono.when(
                        redis.opsForHash().increment(summaryKey, "totalAmount", amount.doubleValue()),
                        redis.opsForHash().increment(summaryKey, "totalRequests", 1),
                        redis.opsForZSet().add(sortedSetKey, member, score)
                ).then()
                .doOnSuccess(v -> log.debug("Summary persistido para {}: {}", processor, amount));
    }

    public Mono<Map<Object, Object>> getSummary(String processor, LocalDateTime from, LocalDateTime to) {
        if (from == null && to == null) {
            return redis.opsForHash().entries(SUMMARY_PREFIX + processor)
                    .collectMap(Map.Entry::getKey, Map.Entry::getValue);
        }

        return calculateSummaryFromSortedSet(processor, from, to);
    }

    private Mono<Map<Object, Object>> calculateSummaryFromSortedSet(String processor, LocalDateTime from, LocalDateTime to) {
        String sortedSetKey = PAYMENT_SORTED_SET_PREFIX + processor;

        double fromScore = (from != null) ? from.toInstant(ZoneOffset.UTC).toEpochMilli() * 1000.0 : Double.NEGATIVE_INFINITY;
        double toScore = (to != null) ? to.toInstant(ZoneOffset.UTC).toEpochMilli() * 1000.0 : Double.POSITIVE_INFINITY;

        return redis.opsForZSet().rangeByScore(sortedSetKey, Range.closed(fromScore, toScore))
                .map(member -> {
                    try {
                        return new BigDecimal(member.split(":")[0]);
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
                        "totalAmount", summary.totalAmount,
                        "totalRequests", summary.totalRequests
                ));
    }

    private static class PaymentSummary {
        BigDecimal totalAmount = BigDecimal.ZERO;
        long totalRequests = 0L;
    }
}