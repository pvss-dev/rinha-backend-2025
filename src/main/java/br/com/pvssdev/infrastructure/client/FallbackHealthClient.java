package br.com.pvssdev.infrastructure.client;

import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

@RegisterRestClient(configKey = "fallback-processor")
public interface FallbackHealthClient extends PaymentProcessorHealthClient {
}
