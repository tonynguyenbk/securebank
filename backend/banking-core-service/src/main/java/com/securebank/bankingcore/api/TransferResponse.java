package com.securebank.bankingcore.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** 201 body of POST /transfers. Stored verbatim in the idempotency record and replayed on retries. */
public record TransferResponse(UUID transactionId, String transactionReference, String status,
                               String sourceAccountNumber, String destinationAccountNumber,
                               String destinationHolderName, BigDecimal amount, String currency, String description,
                               BigDecimal remainingBalance, Instant createdAt, Instant completedAt,
                               List<LedgerLineResponse> ledgerEntries) {
}
