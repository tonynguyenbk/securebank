package com.securebank.common.kafka;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.UUID;

/**
 * Consumer-side idempotency. Call {@link #markIfFirst} inside the consumer's transaction, before doing work:
 * a redelivered event (at-least-once delivery) returns false and is skipped. If processing fails, the
 * insert rolls back with it and the event is processed on retry. Table DDL: docs/contracts/events.md.
 */
public class ProcessedEventStore {

    private final JdbcTemplate jdbc;

    public ProcessedEventStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public boolean markIfFirst(UUID eventId, String consumer) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("markIfFirst must run inside the consumer's transaction");
        }
        int inserted = jdbc.update("""
                INSERT INTO processed_events (event_id, consumer, processed_at)
                VALUES (?, ?, now())
                ON CONFLICT (event_id, consumer) DO NOTHING
                """, eventId, consumer);
        return inserted == 1;
    }
}
