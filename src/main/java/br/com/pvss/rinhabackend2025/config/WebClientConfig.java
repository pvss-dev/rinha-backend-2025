package br.com.pvss.rinhabackend2025.config;

import io.netty.channel.ChannelOption;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;
import reactor.netty.resources.ConnectionProvider;

import java.time.Duration;

@Configuration
@EnableConfigurationProperties(ProcessorPoolProperties.class)
public class WebClientConfig {

    @Bean(destroyMethod = "dispose")
    public ConnectionProvider processorConnectionProvider(ProcessorPoolProperties props) {
        return ConnectionProvider.builder("pp")
                .maxConnections(props.getMaxConnections())
                .pendingAcquireTimeout(props.getAcquireTimeout())
                .pendingAcquireMaxCount(props.getPendingAcquireMaxCount())
                .build();
    }

    private HttpClient httpClient(ConnectionProvider provider) {
        return HttpClient.create(provider)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 300)
                .responseTimeout(Duration.ofMillis(1000));
    }

    @Bean
    @Qualifier("defaultProcessorClient")
    public WebClient defaultProcessorClient(WebClient.Builder builder,
                                            ConnectionProvider processorConnectionProvider,
                                            @Value("${payment.processor.default.url}") String url) {
        return builder
                .clientConnector(new ReactorClientHttpConnector(httpClient(processorConnectionProvider)))
                .baseUrl(url)
                .build();
    }

    @Bean
    @Qualifier("fallbackProcessorClient")
    public WebClient fallbackProcessorClient(WebClient.Builder builder,
                                             ConnectionProvider processorConnectionProvider,
                                             @Value("${payment.processor.fallback.url}") String url) {
        return builder
                .clientConnector(new ReactorClientHttpConnector(httpClient(processorConnectionProvider)))
                .baseUrl(url)
                .build();
    }
}