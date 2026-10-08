package com.securebank.common.outbox;

import com.securebank.common.events.DomainEvent;
import com.securebank.common.json.EventJson;
import com.securebank.common.web.CorrelationId;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

/**
 * Transactional Outbox (spec §18): writes the event row in the SAME database transaction as the
 * business change. Either both commit or neither does; {@link OutboxPublisher} ships rows to Kafka later.
 * Table DDL: docs/contracts/events.md ("outbox_events").
 */
public class OutboxWriter {

    private final JdbcTemplate jdbc;
    private final EventJson json;

    public OutboxWriter(JdbcTemplate jdbc, EventJson json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    public void append(String aggregateType, UUID aggregateId, String topic, String key, DomainEvent event) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("OutboxWriter.append must run inside the business transaction");
        }
        Timestamp now = Timestamp.from(Instant.now());
        jdbc.update("""
                        INSERT INTO outbox_events
                          (id, aggregate_type, aggregate_id, event_type, topic, event_key, payload,
                           correlation_id, status, retry_count, created_at, next_attempt_at)
                        VALUES (?, ?, ?, ?, ?, ?, CAST(? AS jsonb), ?, 'NEW', 0, ?, ?)
                        """,
                event.eventId(), aggregateType, aggregateId, event.eventType(), topic, key,
                json.write(event), CorrelationId.current(), now, now);
    }
}
