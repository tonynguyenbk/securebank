package com.securebank.common.events;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Published (via outbox) when a transfer commits. Carries enough state that fraud and
 * notification services never need to call banking-core back (event-carried state transfer).
 * Topic: {@link Topics#TRANSACTION_COMPLETED}, key: sourceAccountId.
 */
public record TransactionCompletedEvent(
        UUID eventId,
        String eventType,
        int eventVersion,
        Instant occurredAt,
        UUID transactionId,
        String transactionReference,
        UUID sourceAccountId,
        String sourceAccountNumber,
        UUID destinationAccountId,
        String destinationAccountNumber,
        UUID customerId,
        String customerName,
        UUID sourceUserId,
        UUID destinationCustomerId,
        String destinationCustomerName,
        UUID destinationUserId,
        BigDecimal amount,
        String currency,
        String description,
        BigDecimal sourceBalanceAfter,
        BigDecimal destinationBalanceAfter
) implements DomainEvent {

    public static final String TYPE = "TRANSACTION_COMPLETED";
}
