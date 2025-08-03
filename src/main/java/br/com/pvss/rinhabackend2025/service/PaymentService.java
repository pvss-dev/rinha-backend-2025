package br.com.pvss.rinhabackend2025.service;

import br.com.pvss.rinhabackend2025.dto.PaymentDto;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class PaymentService {

    public static final String PAYMENT_QUEUE = "rinha:queue:payments";
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public PaymentService(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    public void queuePayment(PaymentDto dto) {
        try {
            String paymentJson = objectMapper.writeValueAsString(dto);
            redisTemplate.opsForList().leftPush(PAYMENT_QUEUE, paymentJson);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Erro ao serializar PaymentDto para JSON", e);
        }
    }
}