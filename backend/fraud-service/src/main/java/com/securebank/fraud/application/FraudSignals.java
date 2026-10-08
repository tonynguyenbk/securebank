package com.securebank.fraud.application;

import com.securebank.fraud.domain.TransactionSignals;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Port: records an outgoing transfer in the behavioural counters and returns the resulting signals
 * (including that transfer). Implemented with Redis in {@code infrastructure.RedisFraudSignals}.
 */
public interface FraudSignals {

    TransactionSignals recordAndCollect(OutgoingTransfer transfer);

    record OutgoingTransfer(UUID customerId, UUID transactionId, UUID destinationAccountId, BigDecimal amount,
                            Instant occurredAt) {
    }
}
