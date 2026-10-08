package com.securebank.common.events;

import java.time.Instant;
import java.util.UUID;

/**
 * Published by identity-service when a CUSTOMER registers; banking-core creates the customer
 * profile and an empty VND current account. Topic: {@link Topics#USER_REGISTERED}, key: userId.
 */
public record UserRegisteredEvent(
        UUID eventId,
        String eventType,
        int eventVersion,
        Instant occurredAt,
        UUID userId,
        String username,
        String fullName,
        String email,
        String phone
) implements DomainEvent {

    public static final String TYPE = "USER_REGISTERED";
}
