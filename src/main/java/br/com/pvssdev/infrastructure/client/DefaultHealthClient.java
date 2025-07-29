package br.com.pvssdev.infrastructure.client;

import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

@RegisterRestClient(configKey = "default-processor")
public interface DefaultHealthClient extends PaymentProcessorHealthClient { }
