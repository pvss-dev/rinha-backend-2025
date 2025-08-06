package br.com.pvss.rinhabackend2025.service;

import br.com.pvss.rinhabackend2025.dto.PaymentRequestDto;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
public class PaymentQueueService {

    public static final String PAYMENT_QUEUE_KEY = "payments:queue";
    public final ReactiveStringRedisTemplate redis;

    public PaymentQueueService(ReactiveStringRedisTemplate redis) {
        this.redis = redis;
    }

    public Mono<Long> enqueuePayment(PaymentRequestDto request) {
        String payload = request.correlationId().toString() + ":" + request.amount().toPlainString();
        return redis.opsForList().leftPush(PAYMENT_QUEUE_KEY, payload);
    }
}