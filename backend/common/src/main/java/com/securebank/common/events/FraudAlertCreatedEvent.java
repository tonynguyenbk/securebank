package com.securebank.common.events;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Topic: {@link Topics#FRAUD_ALERT_CREATED}, key: customerId. */
public record FraudAlertCreatedEvent(
        UUID eventId,
        String eventType,
        int eventVersion,
        Instant occurredAt,
        UUID alertId,
        UUID transactionId,
        String transactionReference,
        UUID customerId,
        UUID userId,
        UUID sourceAccountId,
        BigDecimal amount,
        String currency,
        int riskScore,
        String riskLevel,
        List<String> triggeredRules
) implements DomainEvent {

    public static final String TYPE = "FRAUD_ALERT_CREATED";
}
