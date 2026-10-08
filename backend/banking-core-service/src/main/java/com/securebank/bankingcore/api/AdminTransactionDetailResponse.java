package com.securebank.bankingcore.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** {@code AdminTransaction & { ledgerEntries: AdminLedgerEntry[] }}. */
public record AdminTransactionDetailResponse(UUID id, String transactionReference, UUID sourceAccountId,
                                             String sourceAccountNumber, String sourceCustomerName,
                                             UUID destinationAccountId, String destinationAccountNumber,
                                             String destinationCustomerName, BigDecimal amount, String currency,
                                             String description, String status, String failureCode,
                                             String failureReason, UUID createdBy, Instant createdAt,
                                             Instant completedAt, List<AdminLedgerEntryResponse> ledgerEntries) {
}
