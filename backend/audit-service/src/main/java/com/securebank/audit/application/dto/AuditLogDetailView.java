package com.securebank.audit.application.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.securebank.audit.domain.AuditLog;
import com.securebank.audit.domain.AuditScope;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** {@code AuditLogDetail} of api.md §5: {@link AuditLogView} plus before/after state. */
public record AuditLogDetailView(
        UUID id,
        UUID eventId,
        Instant occurredAt,
        Instant receivedAt,
        UUID actorUserId,
        String actorUsername,
        String actorRole,
        String action,
        String resourceType,
        String resourceId,
        String outcome,
        String correlationId,
        String sourceService,
        @JsonInclude(JsonInclude.Include.NON_NULL) Optional<String> ipAddress,
        Map<String, Object> before,
        Map<String, Object> after
) {
    public static AuditLogDetailView from(AuditLog a, AuditScope scope) {
        return new AuditLogDetailView(a.getId(), a.getEventId(), a.getOccurredAt(), a.getReceivedAt(),
                a.getActorUserId(), a.getActorUsername(), a.getActorRole(), a.getAction(), a.getResourceType(),
                a.getResourceId(), a.getOutcome(), a.getCorrelationId(), a.getSourceService(),
                AuditLogView.ip(a, scope), a.getBefore(), a.getAfter());
    }
}
