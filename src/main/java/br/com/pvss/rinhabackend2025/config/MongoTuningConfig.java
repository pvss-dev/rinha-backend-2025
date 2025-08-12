package br.com.pvss.rinhabackend2025.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

@Configuration
class MongoTuningConfig {
    @Bean
    org.springframework.boot.autoconfigure.mongo.MongoClientSettingsBuilderCustomizer mongoTuning() {
        return builder -> {
            builder.applyToSocketSettings(b -> b
                    .connectTimeout(150, TimeUnit.MILLISECONDS)
                    .readTimeout(800, TimeUnit.MILLISECONDS)
            );
            builder.applyToConnectionPoolSettings(b -> b
                    .maxSize(24)
                    .minSize(0)
                    .maxConnecting(2)
                    .maxWaitTime(250, TimeUnit.MILLISECONDS)
            );
        };
    }
}