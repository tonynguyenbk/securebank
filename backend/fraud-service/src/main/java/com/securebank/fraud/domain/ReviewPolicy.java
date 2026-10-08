package com.securebank.fraud.domain;

import com.securebank.common.error.ApiException;
import com.securebank.common.error.ErrorCode;

/**
 * Validates an analyst decision before it is applied. Order: the workflow transition first (409
 * {@code FRAUD_ALERT_INVALID_TRANSITION}), then the note rule (400 {@code VALIDATION_FAILED}).
 */
public final class ReviewPolicy {

    public static final int MAX_NOTE_LENGTH = 1000;

    private ReviewPolicy() {
    }

    /** @return the normalized (trimmed, null when blank) note */
    public static String validate(FraudAlertStatus current, FraudAlertStatus target, String note) {
        if (target == null) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "status is required.");
        }
        if (!current.canTransitionTo(target)) {
            throw new ApiException(ErrorCode.FRAUD_ALERT_INVALID_TRANSITION,
                    "A fraud alert in status %s cannot move to %s.".formatted(current, target));
        }
        String normalized = note == null || note.isBlank() ? null : note.strip();
        if (normalized != null && normalized.length() > MAX_NOTE_LENGTH) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED,
                    "note must be at most %d characters.".formatted(MAX_NOTE_LENGTH));
        }
        if (target.requiresNote() && normalized == null) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED,
                    "note is required (1-%d characters) when status is %s.".formatted(MAX_NOTE_LENGTH, target));
        }
        return normalized;
    }
}
