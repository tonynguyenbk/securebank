package com.securebank.fraud.domain;

import java.util.EnumSet;
import java.util.Set;

/**
 * Review workflow (api.md §4):
 * OPEN → UNDER_REVIEW | APPROVED | REJECTED | CLOSED; UNDER_REVIEW → APPROVED | REJECTED | CLOSED;
 * APPROVED | REJECTED → CLOSED; CLOSED is terminal. APPROVED = legitimate, REJECTED = confirmed fraud.
 */
public enum FraudAlertStatus {
    OPEN,
    UNDER_REVIEW,
    APPROVED,
    REJECTED,
    CLOSED;

    public Set<FraudAlertStatus> allowedTargets() {
        return switch (this) {
            case OPEN -> EnumSet.of(UNDER_REVIEW, APPROVED, REJECTED, CLOSED);
            case UNDER_REVIEW -> EnumSet.of(APPROVED, REJECTED, CLOSED);
            case APPROVED, REJECTED -> EnumSet.of(CLOSED);
            case CLOSED -> EnumSet.noneOf(FraudAlertStatus.class);
        };
    }

    public boolean canTransitionTo(FraudAlertStatus target) {
        return target != null && allowedTargets().contains(target);
    }

    /** A decision (APPROVED / REJECTED / CLOSED) must be justified with a note. */
    public boolean requiresNote() {
        return this == APPROVED || this == REJECTED || this == CLOSED;
    }

    /** Alerts still waiting for an analyst. */
    public static Set<FraudAlertStatus> active() {
        return EnumSet.of(OPEN, UNDER_REVIEW);
    }
}
