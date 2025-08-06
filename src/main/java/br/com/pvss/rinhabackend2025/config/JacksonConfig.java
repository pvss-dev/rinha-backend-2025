package br.com.pvss.rinhabackend2025.config;

import br.com.pvss.rinhabackend2025.dto.HealthResponse;
import br.com.pvss.rinhabackend2025.dto.ProcessorPaymentRequest;
import org.springframework.aot.hint.annotation.RegisterReflectionForBinding;
import org.springframework.context.annotation.Configuration;

@Configuration
@RegisterReflectionForBinding({
        ProcessorPaymentRequest.class,
        HealthResponse.class
})
public class JacksonConfig {
}