package br.com.pvss.rinhabackend2025.service;

import br.com.pvss.rinhabackend2025.config.PaymentProcessorManualClient;
import br.com.pvss.rinhabackend2025.dto.HealthStatus;
import br.com.pvss.rinhabackend2025.dto.PaymentRequest;
import br.com.pvss.rinhabackend2025.dto.PaymentSummaryResponse;
import br.com.pvss.rinhabackend2025.exception.PaymentProcessingException;
import br.com.pvss.rinhabackend2025.model.PaymentModel;
import br.com.pvss.rinhabackend2025.repository.PaymentRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Service
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

    private final PaymentProcessorManualClient defaultClient;
    private final PaymentProcessorManualClient fallbackClient;
    private final ProcessorHealthManager healthManager;
    private final ObjectMapper objectMapper;
    private final MongoTemplate mongoTemplate;
    private final int healthyThresholdMs;
    private final PaymentRepository paymentRepository;

    public PaymentService(
            @Qualifier("paymentProcessorDefaultClient") PaymentProcessorManualClient defaultClient,
            @Qualifier("paymentProcessorFallbackClient") PaymentProcessorManualClient fallbackClient,
            ProcessorHealthManager healthManager,
            ObjectMapper objectMapper,
            MongoTemplate mongoTemplate,
            @Value("${strategy.healthy.threshold.ms}") int healthyThresholdMs, PaymentRepository paymentRepository) {
        this.defaultClient = defaultClient;
        this.fallbackClient = fallbackClient;
        this.healthManager = healthManager;
        this.objectMapper = objectMapper;
        this.mongoTemplate = mongoTemplate;
        this.healthyThresholdMs = healthyThresholdMs;
        this.paymentRepository = paymentRepository;
    }

    public CompletableFuture<Void> processPayment(PaymentRequest request) {
        String requestBody;
        try {
            requestBody = objectMapper.writeValueAsString(request);
        } catch (JsonProcessingException e) {
            log.error("correlationId: {}. Falha CRÍTICA ao serializar a requisição.", request.correlationId(), e);
            return CompletableFuture.failedFuture(new PaymentProcessingException("Erro interno ao preparar a requisição.", e));
        }

        HealthStatus defaultStatus = healthManager.getDefaultStatus();
        HealthStatus fallbackStatus = healthManager.getFallbackStatus();

        boolean isDefaultHealthy = !defaultStatus.failing() && defaultStatus.minResponseTime() < healthyThresholdMs;
        boolean isFallbackHealthy = !fallbackStatus.failing() && fallbackStatus.minResponseTime() < healthyThresholdMs;

        if (isDefaultHealthy) {
            return attemptProcessing(defaultClient, "DEFAULT", request, requestBody)
                    .exceptionallyCompose(ex -> {
                        log.warn("correlationId: {}. Falha no DEFAULT, tentando FALLBACK.", request.correlationId(), ex);
                        return attemptProcessing(fallbackClient, "FALLBACK", request, requestBody);
                    });
        } else if (isFallbackHealthy) {
            log.warn("correlationId: {}. Default instável, indo direto para o FALLBACK.", request.correlationId());
            return attemptProcessing(fallbackClient, "FALLBACK", request, requestBody);
        } else {
            log.error("correlationId: {}. Ambos processadores estão instáveis. Rejeitando pagamento.", request.correlationId());
            return CompletableFuture.failedFuture(new PaymentProcessingException("Nenhum processador de pagamento disponível."));
        }
    }

    private CompletableFuture<Void> attemptProcessing(
            PaymentProcessorManualClient client, String processorName, PaymentRequest request, String requestBody) {

        return client.processPaymentAsync(requestBody)
                .thenCompose(success -> {
                    if (success) {
                        PaymentModel payment = new PaymentModel(
                                request.correlationId(), request.amount(), Instant.now(), processorName);

                        return CompletableFuture.runAsync(() -> mongoTemplate.save(payment))
                                .handle((result, exception) -> {
                                    if (exception != null) {
                                        log.error("correlationId: {}. Pagamento processado via {}, MAS FALHOU AO SALVAR NO BANCO.", request.correlationId(), processorName, exception);
                                        throw new PaymentProcessingException("Falha ao persistir o pagamento processado.", exception);
                                    }
                                    log.info("correlationId: {}. Pagamento processado e salvo via {}.", request.correlationId(), processorName);
                                    return null;
                                });
                    } else {
                        log.warn("correlationId: {}. Processador {} recusou o pagamento.", request.correlationId(), processorName);
                        return CompletableFuture.failedFuture(new PaymentProcessingException("Processador recusou o pagamento."));
                    }
                });
    }

    public CompletableFuture<PaymentSummaryResponse> getSummary(Instant from, Instant to) {
        return CompletableFuture.supplyAsync(() -> {
            final Instant queryFrom = (from == null) ? Instant.EPOCH : from;
            final Instant queryTo = (to == null) ? Instant.now() : to;

            List<PaymentModel> payments = paymentRepository.findByRequestedAtBetween(queryFrom, queryTo);

            Map<String, PaymentSummaryResponse.SummaryDetails> summaryMap = payments.stream()
                    .collect(Collectors.groupingBy(
                            PaymentModel::getProcessor,
                            Collectors.reducing(
                                    new PaymentSummaryResponse.SummaryDetails(0L, BigDecimal.ZERO),
                                    p -> new PaymentSummaryResponse.SummaryDetails(1L, p.getAmount()),
                                    (d1, d2) -> new PaymentSummaryResponse.SummaryDetails(
                                            d1.totalRequests() + d2.totalRequests(),
                                            d1.totalAmount().add(d2.totalAmount())
                                    )
                            )
                    ));

            PaymentSummaryResponse.SummaryDetails defaultSummary = summaryMap.getOrDefault("DEFAULT", new PaymentSummaryResponse.SummaryDetails(0L, BigDecimal.ZERO));
            PaymentSummaryResponse.SummaryDetails fallbackSummary = summaryMap.getOrDefault("FALLBACK", new PaymentSummaryResponse.SummaryDetails(0L, BigDecimal.ZERO));

            return new PaymentSummaryResponse(defaultSummary, fallbackSummary);
        });
    }
}