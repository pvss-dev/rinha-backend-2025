package br.com.pvss.rinhabackend2025.dto;

import org.springframework.aot.hint.annotation.RegisterReflectionForBinding;

@RegisterReflectionForBinding
public record HealthResponse(boolean failing, int minResponseTime) {
}