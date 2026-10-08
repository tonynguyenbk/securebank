package com.securebank.bankingcore.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Contract type {@code AdminLedgerEntry}: unmasked ledger line for staff/auditors. */
public record AdminLedgerEntryResponse(UUID id, UUID accountId, String accountNumber, String entryType,
                                       BigDecimal amount, BigDecimal balanceBefore, BigDecimal balanceAfter,
                                       Instant createdAt) {
}
