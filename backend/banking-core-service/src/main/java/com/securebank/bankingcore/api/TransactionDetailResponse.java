package com.securebank.bankingcore.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Contract type {@code TransactionDetail} = TransactionSummary + failureReason, completedAt, ledgerEntries. */
public record TransactionDetailResponse(UUID id, String transactionReference, String direction,
                                        String sourceAccountNumber, String destinationAccountNumber,
                                        String counterpartyName, BigDecimal amount, String currency,
                                        String description, String status, String failureCode, Instant createdAt,
                                        String failureReason, Instant completedAt,
                                        List<LedgerLineResponse> ledgerEntries) {
}
