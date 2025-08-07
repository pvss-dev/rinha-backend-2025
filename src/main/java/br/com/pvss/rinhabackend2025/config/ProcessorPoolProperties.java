package br.com.pvss.rinhabackend2025.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
@ConfigurationProperties(prefix = "payment.processor.pool")
public class ProcessorPoolProperties {
    private int maxConnections = 200;
    private Duration acquireTimeout = Duration.ofSeconds(5);
    private int pendingAcquireMaxCount = -1;

    public int getMaxConnections() {
        return maxConnections;
    }

    public void setMaxConnections(int maxConnections) {
        this.maxConnections = maxConnections;
    }

    public Duration getAcquireTimeout() {
        return acquireTimeout;
    }

    public void setAcquireTimeout(Duration acquireTimeout) {
        this.acquireTimeout = acquireTimeout;
    }

    public int getPendingAcquireMaxCount() {
        return pendingAcquireMaxCount;
    }

    public void setPendingAcquireMaxCount(int pendingAcquireMaxCount) {
        this.pendingAcquireMaxCount = pendingAcquireMaxCount;
    }
}