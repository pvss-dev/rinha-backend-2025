package br.com.pvss.rinhabackend2025.service;

import br.com.pvss.rinhabackend2025.dto.PaymentDto;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Map;

import static br.com.pvss.rinhabackend2025.service.HealthCheckService.PROCESSORS;

@Service
@RequiredArgsConstructor
public class PaymentService {

    private final RedisTemplate<String, String> redisTemplate;
    private static final String STREAM_KEY = "payments_stream";
    private static final String STATUS_KEY = "processor_status";

    public void enqueue(PaymentDto dto) {
        String chosen = chooseProcessor();
        String payload = dto.correlationId() + "," + dto.amount() + "," + chosen;
        redisTemplate.opsForStream().add(STREAM_KEY, Map.of("data", payload));
    }

    private String chooseProcessor() {
        Map<Object, Object> statuses = redisTemplate.opsForHash().entries(STATUS_KEY);
        return statuses.entrySet().stream()
                .map(e -> Map.entry((String) e.getKey(), (String) e.getValue()))
                .filter(entry -> entry.getValue().startsWith("true"))
                .map(entry -> Map.entry(entry.getKey(), Long.parseLong(entry.getValue().split("\\|")[1])))
                .min(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(PROCESSORS[0]);
    }
}