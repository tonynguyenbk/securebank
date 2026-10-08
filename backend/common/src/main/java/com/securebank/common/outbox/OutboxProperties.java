package com.securebank.common.outbox;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * @param enabled      run the scheduled publisher in this service
 * @param pollInterval delay between polls
 * @param batchSize    rows claimed per poll
 * @param maxRetries   attempts before a row stays FAILED for manual inspection
 * @param sendTimeout  max wait for a Kafka acknowledgement
 */
@ConfigurationProperties(prefix = "securebank.outbox")
public record OutboxProperties(boolean enabled, Duration pollInterval, int batchSize, int maxRetries,
                               Duration sendTimeout) {

    public OutboxProperties {
        if (pollInterval == null) {
            pollInterval = Duration.ofMillis(500);
        }
        if (batchSize <= 0) {
            batchSize = 50;
        }
        if (maxRetries <= 0) {
            maxRetries = 10;
        }
        if (sendTimeout == null) {
            sendTimeout = Duration.ofSeconds(5);
        }
    }
}
