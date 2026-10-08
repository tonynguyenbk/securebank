package com.securebank.bankingcore.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Contract type {@code Reconciliation}: does the transaction's ledger balance? */
public record ReconciliationResponse(UUID transactionId, String transactionReference, String status, int entryCount,
                                     BigDecimal debitTotal, BigDecimal creditTotal, boolean balanced,
                                     List<AdminLedgerEntryResponse> entries, Instant checkedAt) {
}
