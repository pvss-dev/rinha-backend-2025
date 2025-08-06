package br.com.pvss.rinhabackend2025.config;

import io.netty.channel.ChannelOption;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;
import reactor.netty.resources.ConnectionProvider;

import java.time.Duration;

@Configuration
public class WebClientConfig {

    @Bean
    public ConnectionProvider connectionProvider() {
        return ConnectionProvider.builder("payment-processor-pool")
                .maxConnections(50)
                .maxIdleTime(Duration.ofSeconds(30))
                .maxLifeTime(Duration.ofMinutes(5))
                .pendingAcquireTimeout(Duration.ofSeconds(3))
                .evictInBackground(Duration.ofSeconds(60))
                .build();
    }

    @Bean
    public HttpClient httpClient(ConnectionProvider connectionProvider) {
        return HttpClient.create(connectionProvider)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 3000)
                .responseTimeout(Duration.ofSeconds(8));
    }

    @Bean
    public WebClient.Builder webClientBuilder(HttpClient httpClient) {
        return WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(httpClient));
    }

    @Bean("defaultProcessorClient")
    public WebClient defaultProcessorClient(
            WebClient.Builder builder,
            @Value("${payment.processor.default.url}") String url
    ) {
        return builder.baseUrl(url).build();
    }

    @Bean("fallbackProcessorClient")
    public WebClient fallbackProcessorClient(
            WebClient.Builder builder,
            @Value("${payment.processor.fallback.url}") String url
    ) {
        return builder.baseUrl(url).build();
    }
}