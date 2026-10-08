package com.securebank.bankingcore.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Contract type {@code AdminTransaction}. */
public record AdminTransactionResponse(UUID id, String transactionReference, UUID sourceAccountId,
                                       String sourceAccountNumber, String sourceCustomerName,
                                       UUID destinationAccountId, String destinationAccountNumber,
                                       String destinationCustomerName, BigDecimal amount, String currency,
                                       String description, String status, String failureCode, String failureReason,
                                       UUID createdBy, Instant createdAt, Instant completedAt) {
}
