package br.com.pvss.rinhabackend2025.service;

import br.com.pvss.rinhabackend2025.dto.ProcessorType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class RedisSummaryService {

    private static final Logger log = LoggerFactory.getLogger(RedisSummaryService.class);
    private static final String CORRELATION_PREFIX = "correlation:";
    private static final String SUMMARY_PREFIX = "summary:";
    private static final String PAYMENT_PREFIX = "payment:";
    private static final Duration CORRELATION_TTL = Duration.ofHours(2);
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private final ReactiveStringRedisTemplate redis;
    private final Map<String, CachedSummary> summaryCache = new ConcurrentHashMap<>();

    public RedisSummaryService(ReactiveStringRedisTemplate redis) {
        this.redis = redis;
    }

    public Mono<Boolean> isAlreadyProcessed(String correlationId) {
        return redis.hasKey(CORRELATION_PREFIX + correlationId)
                .doOnNext(exists -> {
                    if (Boolean.TRUE.equals(exists)) {
                        log.debug("Correlation ID já processado: {}", correlationId);
                    }
                });
    }

    public Mono<Void> markAsProcessed(String correlationId) {
        return redis.opsForValue()
                .set(CORRELATION_PREFIX + correlationId, "1", CORRELATION_TTL)
                .then();
    }

    public Mono<Void> persistPaymentSummary(ProcessorType processor, BigDecimal amount) {
        String processorKey = processor.name().toLowerCase();
        String summaryKey = SUMMARY_PREFIX + processorKey;
        String timestamp = LocalDateTime.now().format(FORMATTER);
        String paymentKey = PAYMENT_PREFIX + processorKey + ":" + System.currentTimeMillis();

        summaryCache.remove(processorKey);

        return Mono.when(
                        redis.opsForHash().increment(summaryKey, "totalAmount", amount.doubleValue()),
                        redis.opsForHash().increment(summaryKey, "totalRequests", 1),
                        redis.opsForValue().set(paymentKey, amount + ":" + timestamp, Duration.ofHours(24))
                ).then()
                .doOnSuccess(v -> log.debug("Payment summary persistido - Processor: {}, Amount: {}", processor, amount));
    }

    public Mono<Map<Object, Object>> getSummary(String processor, LocalDateTime from, LocalDateTime to) {
        String cacheKey = processor + ":" + from + ":" + to;
        CachedSummary cached = summaryCache.get(cacheKey);

        if (cached != null && !cached.isExpired()) {
            log.debug("Retornando summary do cache para: {}", cacheKey);
            return Mono.just(cached.getData());
        }

        return calculateSummary(processor, from, to)
                .doOnNext(data -> {
                    summaryCache.put(cacheKey, new CachedSummary(data, Duration.ofSeconds(5)));
                    log.debug("Summary calculado e cacheado para: {} - {}", cacheKey, data);
                });
    }

    private Mono<Map<Object, Object>> calculateSummary(String processor, LocalDateTime from, LocalDateTime to) {
        if (from == null && to == null) {
            return redis.opsForHash().entries(SUMMARY_PREFIX + processor)
                    .collectMap(Map.Entry::getKey, Map.Entry::getValue);
        }

        return redis.keys(PAYMENT_PREFIX + processor + ":*")
                .flatMap(key -> redis.opsForValue().get(key)
                        .map(value -> {
                            String[] parts = value.split(":");
                            if (parts.length >= 2) {
                                try {
                                    BigDecimal amount = new BigDecimal(parts[0]);
                                    LocalDateTime timestamp = LocalDateTime.parse(parts[1], FORMATTER);

                                    if ((from == null || !timestamp.isBefore(from)) &&
                                        (to == null || !timestamp.isAfter(to))) {
                                        return amount;
                                    }
                                } catch (Exception e) {
                                    log.warn("Erro ao processar pagamento: {} - {}", key, value, e);
                                }
                            }
                            return BigDecimal.ZERO;
                        }))
                .reduce(new PaymentSummary(), (summary, amount) -> {
                    if (amount.compareTo(BigDecimal.ZERO) > 0) {
                        summary.totalAmount = summary.totalAmount.add(amount);
                        summary.totalRequests++;
                    }
                    return summary;
                })
                .map(summary -> Map.of(
                        "totalAmount", summary.totalAmount.toString(),
                        "totalRequests", String.valueOf(summary.totalRequests)
                ));
    }

    private static class PaymentSummary {
        BigDecimal totalAmount = BigDecimal.ZERO;
        long totalRequests = 0L;
    }

    private static class CachedSummary {
        private final Map<Object, Object> data;
        private final long timestamp;
        private final Duration ttl;

        CachedSummary(Map<Object, Object> data, Duration ttl) {
            this.data = data;
            this.timestamp = System.currentTimeMillis();
            this.ttl = ttl;
        }

        boolean isExpired() {
            return System.currentTimeMillis() - timestamp > ttl.toMillis();
        }

        Map<Object, Object> getData() {
            return data;
        }
    }
}