package br.com.pvss.rinhabackend2025.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.concurrent.Executors;

@Configuration
public class HttpClientConfiguration {

    @Bean
    public HttpClient httpClient() {
        return HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NEVER)
                .version(HttpClient.Version.HTTP_1_1)
                .executor(Executors.newVirtualThreadPerTaskExecutor())
                .connectTimeout(Duration.ofMillis(1000))
                .build();
    }
}