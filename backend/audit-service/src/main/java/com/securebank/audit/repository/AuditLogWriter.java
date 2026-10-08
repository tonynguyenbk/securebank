package com.securebank.audit.repository;

import com.securebank.common.events.AuditEvent;
import com.securebank.common.json.EventJson;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Inserts audit records. {@code ON CONFLICT (event_id) DO NOTHING} is the consumer's idempotency mechanism:
 * a redelivered event is silently ignored.
 */
@Repository
public class AuditLogWriter {

    private final JdbcTemplate jdbc;
    private final EventJson json;

    public AuditLogWriter(JdbcTemplate jdbc, EventJson json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    /** @return true when the record was inserted, false for a duplicate event */
    public boolean insertIfAbsent(AuditEvent e, String outcome, Instant receivedAt) {
        int inserted = jdbc.update("""
                        INSERT INTO audit_logs (id, event_id, occurred_at, received_at, actor_user_id, actor_username,
                            actor_role, action, resource_type, resource_id, before, after, outcome, correlation_id,
                            ip_address, source_service)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CAST(? AS jsonb), CAST(? AS jsonb), ?, ?, ?, ?)
                        ON CONFLICT (event_id) DO NOTHING
                        """,
                UUID.randomUUID(), e.eventId(),
                Timestamp.from(e.occurredAt() != null ? e.occurredAt() : receivedAt), Timestamp.from(receivedAt),
                e.actorUserId(), truncate(e.actorUsername(), 100), truncate(e.actorRole(), 30), e.action(),
                e.resourceType(), truncate(e.resourceId(), 100), toJson(e.before()), toJson(e.after()), outcome,
                truncate(e.correlationId(), 64), truncate(e.ipAddress(), 64), e.sourceService());
        return inserted == 1;
    }

    private String toJson(Map<String, Object> value) {
        return value == null ? null : json.write(value);
    }

    private static String truncate(String s, int max) {
        return s == null || s.length() <= max ? s : s.substring(0, max);
    }
}
