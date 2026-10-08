package com.securebank.bankingcore.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Contract type {@code TransactionSummary}; direction and counterparty are relative to the caller. */
public record TransactionSummaryResponse(UUID id, String transactionReference, String direction,
                                         String sourceAccountNumber, String destinationAccountNumber,
                                         String counterpartyName, BigDecimal amount, String currency,
                                         String description, String status, String failureCode, Instant createdAt) {
}
