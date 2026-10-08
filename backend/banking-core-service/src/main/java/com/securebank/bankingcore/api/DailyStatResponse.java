package com.securebank.bankingcore.api;

import java.math.BigDecimal;
import java.time.LocalDate;

/** One day of GET /admin/stats/daily (SUCCESS transfers only). */
public record DailyStatResponse(LocalDate date, long count, BigDecimal amount) {
}
