package com.securebank.bankingcore.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Contract type {@code StatementEntry}: one passbook line of an own account. */
public record StatementEntryResponse(UUID id, UUID transactionId, String transactionReference, String entryType,
                                     BigDecimal amount, BigDecimal balanceBefore, BigDecimal balanceAfter,
                                     String counterpartyAccountNumber, String counterpartyName, String description,
                                     Instant createdAt) {
}
