package com.securebank.common.events;

import java.time.Instant;
import java.util.UUID;

/** Envelope fields present on every event (docs/contracts/events.md). eventId drives consumer idempotency. */
public interface DomainEvent {

    UUID eventId();

    String eventType();

    int eventVersion();

    Instant occurredAt();
}
