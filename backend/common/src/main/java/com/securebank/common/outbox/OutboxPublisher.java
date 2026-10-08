package com.securebank.common.outbox;

import com.securebank.common.web.CorrelationId;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Polls NEW/FAILED outbox rows and publishes them to Kafka (spec §18).
 *
 * <p>Delivery is <b>at-least-once</b>: if the process dies after Kafka acknowledged but before the row
 * is marked PUBLISHED, the event is sent again. Consumers deduplicate by eventId
 * ({@code ProcessedEventStore}). {@code FOR UPDATE SKIP LOCKED} lets several instances poll safely.
 */
public class OutboxPublisher {

    public static final String HEADER_EVENT_ID = "eventId";
    public static final String HEADER_EVENT_TYPE = "eventType";

    private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);

    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final KafkaTemplate<String, String> kafka;
    private final OutboxProperties properties;
    private final Counter publishFailures;
    private final Counter published;

    public OutboxPublisher(JdbcTemplate jdbc, TransactionTemplate tx, KafkaTemplate<String, String> kafka,
                           OutboxProperties properties, MeterRegistry meters) {
        this.jdbc = jdbc;
        this.tx = tx;
        this.kafka = kafka;
        this.properties = properties;
        this.publishFailures = Counter.builder("kafka_publish_failure_total")
                .description("Outbox rows that failed to publish").register(meters);
        this.published = Counter.builder("outbox_published_total")
                .description("Outbox rows published to Kafka").register(meters);
        Gauge.builder("outbox_pending_count", this, OutboxPublisher::pendingCount)
                .description("Outbox rows not yet published").register(meters);
    }

    @Scheduled(fixedDelayString = "${securebank.outbox.poll-interval:500ms}")
    public void publishPending() {
        tx.executeWithoutResult(status -> {
            List<Row> rows = jdbc.query("""
                            SELECT id, topic, event_key, event_type, payload::text AS payload, correlation_id, retry_count
                            FROM outbox_events
                            WHERE status IN ('NEW', 'FAILED') AND retry_count < ? AND next_attempt_at <= now()
                            ORDER BY created_at
                            LIMIT ?
                            FOR UPDATE SKIP LOCKED
                            """,
                    (rs, i) -> new Row(rs.getObject("id", UUID.class), rs.getString("topic"),
                            rs.getString("event_key"), rs.getString("event_type"), rs.getString("payload"),
                            rs.getString("correlation_id"), rs.getInt("retry_count")),
                    properties.maxRetries(), properties.batchSize());
            for (Row row : rows) {
                publish(row);
            }
        });
    }

    private void publish(Row row) {
        try {
            ProducerRecord<String, String> record = new ProducerRecord<>(row.topic(), row.key(), row.payload());
            record.headers().add(HEADER_EVENT_ID, row.id().toString().getBytes(StandardCharsets.UTF_8));
            record.headers().add(HEADER_EVENT_TYPE, row.eventType().getBytes(StandardCharsets.UTF_8));
            if (row.correlationId() != null) {
                record.headers().add(CorrelationId.HEADER, row.correlationId().getBytes(StandardCharsets.UTF_8));
            }
            kafka.send(record).get(properties.sendTimeout().toMillis(), TimeUnit.MILLISECONDS);
            jdbc.update("UPDATE outbox_events SET status = 'PUBLISHED', published_at = now(), last_error = NULL WHERE id = ?",
                    row.id());
            published.increment();
        } catch (Exception e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            int attempt = row.retryCount() + 1;
            Duration backoff = Duration.ofSeconds(Math.min(300, 1L << Math.min(attempt, 8)));
            jdbc.update("""
                            UPDATE outbox_events
                            SET status = 'FAILED', retry_count = ?, last_error = ?, next_attempt_at = ?
                            WHERE id = ?
                            """,
                    attempt, truncate(e.toString()), Timestamp.from(Instant.now().plus(backoff)), row.id());
            publishFailures.increment();
            log.warn("Outbox publish failed eventId={} type={} attempt={} nextRetryIn={}s: {}",
                    row.id(), row.eventType(), attempt, backoff.toSeconds(), e.toString());
        }
    }

    private double pendingCount() {
        try {
            Long n = jdbc.queryForObject("SELECT count(*) FROM outbox_events WHERE status <> 'PUBLISHED'", Long.class);
            return n == null ? 0 : n;
        } catch (RuntimeException e) {
            return Double.NaN;
        }
    }

    private static String truncate(String s) {
        return s.length() > 500 ? s.substring(0, 500) : s;
    }

    private record Row(UUID id, String topic, String key, String eventType, String payload, String correlationId,
                       int retryCount) {
    }
}
