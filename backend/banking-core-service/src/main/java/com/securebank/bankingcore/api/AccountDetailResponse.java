package com.securebank.bankingcore.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Contract type {@code AccountDetail} = AccountSummary + customerId, limits, updatedAt. */
public record AccountDetailResponse(UUID id, String accountNumber, String type, String currency, BigDecimal balance,
                                    String status, Instant createdAt, UUID customerId, TransferLimitsResponse limits,
                                    Instant updatedAt) {
}
