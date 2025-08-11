package br.com.pvss.rinhabackend2025.config;

import br.com.pvss.rinhabackend2025.dto.ProcessorPaymentRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;

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

    @Bean
    public BlockingQueue<ProcessorPaymentRequest> paymentQueue(
            @Value("${queue.capacity:5000}") int capacity) {
        return new LinkedBlockingQueue<>(capacity);
    }
}