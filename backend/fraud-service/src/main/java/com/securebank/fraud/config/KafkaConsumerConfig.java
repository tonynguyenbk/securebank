package com.securebank.fraud.config;

import com.securebank.fraud.infrastructure.MalformedEventException;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.ExponentialBackOffWithMaxRetries;

import java.time.Duration;

/**
 * Consumer error handling (picked up by Boot's listener container factory): transient failures are retried
 * with exponential backoff; after the last attempt — or immediately for malformed payloads — the record is
 * logged and skipped so one poison message cannot block its partition. The payload itself is not logged
 * (it may contain personal data); topic/partition/offset are enough to find it again.
 */
@Configuration(proxyBeanMethods = false)
public class KafkaConsumerConfig {

    private static final Logger log = LoggerFactory.getLogger(KafkaConsumerConfig.class);

    @Bean
    public CommonErrorHandler kafkaErrorHandler(
            @Value("${securebank.kafka.consumer.max-attempts:4}") int maxAttempts,
            @Value("${securebank.kafka.consumer.initial-backoff:500ms}") Duration initialBackoff,
            MeterRegistry meters) {
        var backOff = new ExponentialBackOffWithMaxRetries(Math.max(0, maxAttempts - 1));
        backOff.setInitialInterval(initialBackoff.toMillis());
        backOff.setMultiplier(2.0);
        backOff.setMaxInterval(10_000);
        var handler = new DefaultErrorHandler((record, ex) -> {
            meters.counter("kafka_consumer_skipped_total", "topic", record.topic()).increment();
            log.error("Skipping Kafka record topic={} partition={} offset={} after failure: {}",
                    record.topic(), record.partition(), record.offset(), rootMessage(ex));
        }, backOff);
        handler.addNotRetryableExceptions(MalformedEventException.class);
        return handler;
    }

    private static String rootMessage(Throwable ex) {
        Throwable t = ex;
        while (t.getCause() != null && t.getCause() != t) {
            t = t.getCause();
        }
        return t.getClass().getSimpleName() + ": " + t.getMessage();
    }
}
