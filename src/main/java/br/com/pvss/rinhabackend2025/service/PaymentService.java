package br.com.pvss.rinhabackend2025.service;

import br.com.pvss.rinhabackend2025.dto.PaymentDto;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PaymentService {

    public static final String PAYMENT_QUEUE = "rinha:queue:payments";
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    @SneakyThrows
    public void queuePayment(PaymentDto dto) {
        String paymentJson = objectMapper.writeValueAsString(dto);
        redisTemplate.opsForList().leftPush(PAYMENT_QUEUE, paymentJson);
    }
}