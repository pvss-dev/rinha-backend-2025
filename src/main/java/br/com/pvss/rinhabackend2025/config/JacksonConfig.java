package br.com.pvss.rinhabackend2025.config;

import br.com.pvss.rinhabackend2025.dto.HealthResponse;
import br.com.pvss.rinhabackend2025.dto.ProcessorPaymentRequest;
import br.com.pvss.rinhabackend2025.dto.SummaryItem;
import br.com.pvss.rinhabackend2025.dto.SummaryResponse;
import org.springframework.aot.hint.annotation.RegisterReflectionForBinding;
import org.springframework.context.annotation.Configuration;

@Configuration
@RegisterReflectionForBinding({
        ProcessorPaymentRequest.class,
        HealthResponse.class,
        SummaryItem.class,
        SummaryResponse.class
})
public class JacksonConfig {
}