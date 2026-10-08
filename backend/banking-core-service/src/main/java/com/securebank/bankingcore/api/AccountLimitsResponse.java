package com.securebank.bankingcore.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** {@code TransferLimits & { accountId }} for the admin limits endpoints. */
public record AccountLimitsResponse(UUID accountId, BigDecimal perTransactionLimit, BigDecimal dailyLimit,
                                    BigDecimal usedToday, BigDecimal remainingToday, Instant updatedAt) {
}
