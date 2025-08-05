package br.com.pvss.rinhabackend2025.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.aot.hint.annotation.RegisterReflectionForBinding;

@RegisterReflectionForBinding
public record HealthResponse(
        @JsonProperty("failing")
        boolean failing,
        @JsonProperty("minResponseTime")
        int minResponseTime
) {
}