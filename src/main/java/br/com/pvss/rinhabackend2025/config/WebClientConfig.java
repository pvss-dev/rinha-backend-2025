package br.com.pvss.rinhabackend2025.config;

import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;
import reactor.netty.resources.ConnectionProvider;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

@Configuration
public class WebClientConfig {

    @Bean
    public ConnectionProvider connectionProvider() {
        return ConnectionProvider.builder("processor-pool")
                .maxConnections(20)
                .maxIdleTime(Duration.ofSeconds(20))
                .maxLifeTime(Duration.ofSeconds(60))
                .pendingAcquireTimeout(Duration.ofSeconds(5))
                .evictInBackground(Duration.ofSeconds(120))
                .build();
    }

    @Bean
    public HttpClient httpClient(ConnectionProvider connectionProvider) {
        return HttpClient.create(connectionProvider)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 5000)
                .responseTimeout(Duration.ofSeconds(10))
                .doOnConnected(conn ->
                        conn.addHandlerLast(new ReadTimeoutHandler(10, TimeUnit.SECONDS))
                                .addHandlerLast(new WriteTimeoutHandler(10, TimeUnit.SECONDS)));
    }

    @Bean
    public WebClient.Builder webClientBuilder(HttpClient httpClient) {
        return WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(1024 * 1024));
    }

    @Bean
    public WebClient defaultProcessorClient(
            WebClient.Builder builder,
            @Value("${payment.processor.default.url}") String url
    ) {
        return builder.baseUrl(url).build();
    }

    @Bean
    public WebClient fallbackProcessorClient(
            WebClient.Builder builder,
            @Value("${payment.processor.fallback.url}") String url
    ) {
        return builder.baseUrl(url).build();
    }
}