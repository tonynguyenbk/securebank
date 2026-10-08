package com.securebank.bankingcore.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** GET /accounts/{id}/balance. */
public record AccountBalanceResponse(UUID accountId, String accountNumber, BigDecimal balance, String currency,
                                     String status, Instant asOf) {
}
