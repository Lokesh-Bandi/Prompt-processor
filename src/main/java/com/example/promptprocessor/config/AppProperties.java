package com.example.promptprocessor.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public record AppProperties(
        ThreadPoolProperties threadPool,
        InferenceProperties inference,
        RetryProperties retry
) {
    public record ThreadPoolProperties(
            int coreSize,
            int maxSize,
            int queueCapacity,
            int keepAliveSeconds
    ) {}

    public record InferenceProperties(
            long mockDelayMinMs,
            long mockDelayMaxMs,
            double rateLimitProbability,
            int rateLimitEveryNth
    ) {}

    public record RetryProperties(
            int maxRetries,
            long backoffMs
    ) {}
}
