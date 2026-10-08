package com.securebank.bankingcore.application.query;

import com.securebank.bankingcore.domain.TransactionStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Optional filters shared by the customer and admin transaction lists. Dates are inclusive business days
 * (Asia/Ho_Chi_Minh). {@code accountId} is used by the customer list, {@code accountNumber} and
 * {@code reference} by the admin list.
 */
public record TransactionFilter(UUID accountId, String accountNumber, String reference, TransactionStatus status,
                                LocalDate fromDate, LocalDate toDate, BigDecimal minAmount, BigDecimal maxAmount) {
}
