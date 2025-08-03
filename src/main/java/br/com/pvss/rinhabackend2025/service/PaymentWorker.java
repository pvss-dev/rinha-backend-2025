package br.com.pvss.rinhabackend2025.service;

import br.com.pvss.rinhabackend2025.client.PaymentProcessorClient;
import br.com.pvss.rinhabackend2025.dto.PaymentRequestDto;
import br.com.pvss.rinhabackend2025.entity.PaymentRequestEntity;
import br.com.pvss.rinhabackend2025.repository.PaymentRequestRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.scheduler.Schedulers;

import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentWorker {

    private final PaymentRequestRepository repository;
    private final PaymentProcessorClient processorClient;
    private final RedisTemplate<String, String> redisTemplate;
    private final PaymentTransactionHandler transactionHandler;

    @Async("taskExecutor")
    @Transactional
    public void processPendingPayments() {
        List<PaymentRequestEntity> pending = repository.findTop10ByStatusOrderByCreatedAtAsc(PaymentRequestEntity.PaymentStatus.PENDING);

        for (PaymentRequestEntity request : pending) {
            request.setStatus(PaymentRequestEntity.PaymentStatus.PROCESSING);
            repository.save(request);

            String chosenProcessor = chooseProcessor();

            PaymentRequestDto requestDto = new PaymentRequestDto(
                    request.getCorrelationId().toString(),
                    request.getAmount(),
                    request.getCreatedAt().toString()
            );

            processorClient.process(requestDto, chosenProcessor)
                    .publishOn(Schedulers.boundedElastic())
                    .doOnSuccess(v -> transactionHandler.handleSuccess(request, chosenProcessor))
                    .doOnError(e -> transactionHandler.handleError(request))
                    .subscribe();
        }
    }

    private String chooseProcessor() {
        Map<Object, Object> statuses = redisTemplate.opsForHash().entries("processor_status");
        return statuses.entrySet().stream()
                .map(e -> Map.entry((String) e.getKey(), (String) e.getValue()))
                .filter(entry -> entry.getValue().startsWith("healthy"))
                .map(entry -> Map.entry(entry.getKey(), Long.parseLong(entry.getValue().split("\\|")[1])))
                .min(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(HealthCheckService.PROCESSORS[0]);
    }
}