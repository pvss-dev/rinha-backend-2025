package br.com.pvss.rinhabackend2025.config;

import br.com.pvss.rinhabackend2025.dto.*;
import org.springframework.aot.hint.annotation.RegisterReflectionForBinding;
import org.springframework.context.annotation.Configuration;

@Configuration
@RegisterReflectionForBinding({
        HealthResponse.class,
        PaymentRequestDto.class,
        PaymentsSummaryResponse.class,
        ProcessorPaymentRequest.class,
        SummaryItem.class,
        SummaryResponse.class
})
public class JacksonConfig {
}