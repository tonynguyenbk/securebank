package com.securebank.bankingcore.api;

import java.math.BigDecimal;
import java.time.Instant;

/** Contract type {@code TransferLimits}: configured limits plus today's usage (Asia/Ho_Chi_Minh day). */
public record TransferLimitsResponse(BigDecimal perTransactionLimit, BigDecimal dailyLimit, BigDecimal usedToday,
                                     BigDecimal remainingToday, Instant updatedAt) {
}
