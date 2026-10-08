package com.securebank.common.events;

import java.time.Instant;
import java.util.UUID;

/** Small helpers for building event envelopes consistently. */
public final class Events {

    public static final int VERSION_1 = 1;

    private Events() {
    }

    public static UUID newId() {
        return UUID.randomUUID();
    }

    public static Instant now() {
        return Instant.now();
    }
}
