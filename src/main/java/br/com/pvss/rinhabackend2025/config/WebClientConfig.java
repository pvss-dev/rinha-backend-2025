package br.com.pvss.rinhabackend2025.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;


@Configuration
public class WebClientConfig {

    @Bean
    public WebClient defaultProcessorClient(
            WebClient.Builder builder,
            @Value("${payment.processor.default.url}")
            String url
    ) {
        return builder.baseUrl(url).build();
    }

    @Bean
    public WebClient fallbackProcessorClient(
            WebClient.Builder builder,
            @Value("${payment.processor.fallback.url}")
            String url
    ) {
        return builder.baseUrl(url).build();
    }
}