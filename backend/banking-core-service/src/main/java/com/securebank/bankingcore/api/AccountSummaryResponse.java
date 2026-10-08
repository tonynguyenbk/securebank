package com.securebank.bankingcore.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Contract type {@code AccountSummary}. */
public record AccountSummaryResponse(UUID id, String accountNumber, String type, String currency, BigDecimal balance,
                                     String status, Instant createdAt) {
}
