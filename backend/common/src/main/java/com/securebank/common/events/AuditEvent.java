package com.securebank.common.events;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Who did what, when, to which resource, with before/after state (spec §20).
 * Produced by every service through its outbox; consumed and stored append-only by audit-service.
 * Topic: {@link Topics#AUDIT_EVENT}, key: resourceId.
 */
public record AuditEvent(
        UUID eventId,
        String eventType,
        int eventVersion,
        Instant occurredAt,
        UUID actorUserId,
        String actorUsername,
        String actorRole,
        String action,
        String resourceType,
        String resourceId,
        Map<String, Object> before,
        Map<String, Object> after,
        String correlationId,
        String ipAddress,
        String sourceService,
        String outcome
) implements DomainEvent {

    public static final String TYPE = "AUDIT";
    public static final String OUTCOME_SUCCESS = "SUCCESS";
    public static final String OUTCOME_FAILURE = "FAILURE";
}
