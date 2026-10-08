package com.securebank.common.events;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Published when a transfer is REJECTED by a business rule (insufficient funds, limits, frozen...).
 * Topic: {@link Topics#TRANSACTION_FAILED}, key: sourceAccountId.
 */
public record TransactionFailedEvent(
        UUID eventId,
        String eventType,
        int eventVersion,
        Instant occurredAt,
        UUID transactionId,
        String transactionReference,
        UUID sourceAccountId,
        String sourceAccountNumber,
        String destinationAccountNumber,
        UUID customerId,
        UUID sourceUserId,
        BigDecimal amount,
        String currency,
        String status,
        String failureCode,
        String failureReason
) implements DomainEvent {

    public static final String TYPE = "TRANSACTION_FAILED";
}
