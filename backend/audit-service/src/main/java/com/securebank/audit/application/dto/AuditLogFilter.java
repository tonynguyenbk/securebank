package com.securebank.audit.application.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Optional filters of {@code GET /audit/logs}; null means "any".
 *
 * @param actor username "contains" match, case-insensitive
 * @param from  inclusive lower bound on occurredAt
 * @param to    exclusive upper bound on occurredAt
 */
public record AuditLogFilter(String actor, UUID actorUserId, String action, String resourceType, String resourceId,
                             Instant from, Instant to, String correlationId) {
}
