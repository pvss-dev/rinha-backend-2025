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
    public ConnectionProvider processorConnectionProvider(ProcessorPoolProperties props) {
        return ConnectionProvider.builder("processor-pool")
                .maxConnections(props.getMaxConnections())
                .pendingAcquireTimeout(props.getAcquireTimeout())
                .pendingAcquireMaxCount(props.getPendingAcquireMaxCount())
                .build();
    }

    @Bean("defaultProcessorClient")
    public WebClient defaultProcessorClient(
            WebClient.Builder builder,
            ConnectionProvider processorConnectionProvider,
            @Value("${payment.processor.default.url}") String url
    ) {
        HttpClient httpClient = HttpClient.create(processorConnectionProvider)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 5_000)
                .responseTimeout(Duration.ofSeconds(5));

        return builder
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .baseUrl(url)
                .build();
    }

    @Bean("fallbackProcessorClient")
    public WebClient fallbackProcessorClient(
            WebClient.Builder builder,
            ConnectionProvider processorConnectionProvider,
            @Value("${payment.processor.fallback.url}") String url
    ) {
        HttpClient httpClient = HttpClient.create(processorConnectionProvider)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 5_000)
                .responseTimeout(Duration.ofSeconds(5));

        return builder
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .baseUrl(url)
                .build();
    }
}