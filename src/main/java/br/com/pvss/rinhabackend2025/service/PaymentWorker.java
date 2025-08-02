package br.com.pvss.rinhabackend2025.service;

import br.com.pvss.rinhabackend2025.client.PaymentProcessorClient;
import br.com.pvss.rinhabackend2025.dto.PaymentDto;
import br.com.pvss.rinhabackend2025.entity.PaymentEntity;
import br.com.pvss.rinhabackend2025.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.connection.stream.StreamReadOptions;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StreamOperations;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.scheduling.annotation.EnableScheduling;

@Component
@EnableScheduling
@RequiredArgsConstructor
public class PaymentWorker {

    private final RedisTemplate<String, String> redisTemplate;
    private final PaymentProcessorClient processorClient;
    private final PaymentRepository repository;
    private final ObjectMapper objectMapper;
    private static final String STREAM_KEY = "payments_stream";

    @Scheduled(fixedDelay = 500)
    public void pollStream() {
        StreamOperations<String, String, String> streams = redisTemplate.opsForStream();
        List<MapRecord<String, String, String>> messages =
                streams.read(
                        StreamReadOptions.empty().count(10).block(Duration.ZERO),
                        StreamOffset.create(STREAM_KEY, ReadOffset.lastConsumed())
                );

        assert messages != null;
        for (MapRecord<String, String, String> record : messages) {
            try {
                String[] parts = record.getValue().get("data").split(",");
                PaymentDto dto = new PaymentDto(parts[0], new java.math.BigDecimal(parts[1]));
                String processorUrl = parts[2];

                processorClient.process(dto)
                        .subscribeOn(Schedulers.boundedElastic())
                        .subscribe(v -> handleSuccess(dto, processorUrl), e -> handleError(dto, e));

            } finally {
                streams.delete(STREAM_KEY, record.getId());
            }
        }
    }

    private void handleSuccess(PaymentDto dto, String processor) {
        PaymentEntity entity = PaymentEntity.builder()
                .correlationId(UUID.fromString(dto.correlationId()))
                .amount(dto.amount())
                .processor(processor)
                .requestedAt(Instant.now())
                .processedAt(Instant.now())
                .build();
        repository.save(entity);
        redisTemplate.opsForValue().increment("summary:requests");
        redisTemplate.opsForValue().increment("summary:amount", dto.amount().longValue());
    }

    private void handleError(PaymentDto dto, Throwable err) {
        // log or handle error
    }
}