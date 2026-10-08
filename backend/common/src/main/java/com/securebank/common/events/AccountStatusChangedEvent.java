package com.securebank.common.events;

import java.time.Instant;
import java.util.UUID;

/** Topic: {@link Topics#ACCOUNT_STATUS_CHANGED}, key: accountId. */
public record AccountStatusChangedEvent(
        UUID eventId,
        String eventType,
        int eventVersion,
        Instant occurredAt,
        UUID accountId,
        String accountNumber,
        UUID customerId,
        UUID userId,
        String previousStatus,
        String newStatus,
        String reason,
        UUID actorUserId
) implements DomainEvent {

    public static final String TYPE = "ACCOUNT_STATUS_CHANGED";
}
