package com.securebank.audit.application.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.securebank.audit.domain.AuditLog;
import com.securebank.audit.domain.AuditScope;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * {@code AuditLog} of api.md §5.
 *
 * <p>{@code ipAddress} must be <i>omitted</i> for BANK_STAFF but present (possibly {@code null}) for AUDITOR/ADMIN.
 * It is therefore an {@link Optional} with {@code NON_NULL}: a {@code null} reference drops the field (restricted
 * view), {@code Optional.empty()} serializes as JSON {@code null} (full view, no IP recorded).
 */
public record AuditLogView(
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
        @JsonInclude(JsonInclude.Include.NON_NULL) Optional<String> ipAddress
) {
    public static AuditLogView from(AuditLog a, AuditScope scope) {
        return new AuditLogView(a.getId(), a.getEventId(), a.getOccurredAt(), a.getReceivedAt(), a.getActorUserId(),
                a.getActorUsername(), a.getActorRole(), a.getAction(), a.getResourceType(), a.getResourceId(),
                a.getOutcome(), a.getCorrelationId(), a.getSourceService(), ip(a, scope));
    }

    static Optional<String> ip(AuditLog a, AuditScope scope) {
        return scope.showsIpAddress() ? Optional.ofNullable(a.getIpAddress()) : null;
    }
}
