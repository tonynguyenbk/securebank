package com.securebank.bankingcore.api;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Contract type {@code OpsStatsToday} (date in Asia/Ho_Chi_Minh). */
public record OpsStatsTodayResponse(LocalDate date, long transactionsToday, long successfulToday,
                                    long failedOrRejectedToday, BigDecimal totalTransferredToday, long frozenAccounts,
                                    String currency) {
}
