package com.securebank.bankingcore.api;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Contract type {@code LedgerLine}: one side of the double entry as seen by a customer. For the counterparty's
 * line the account number is masked and both balances are null (privacy rule, contract §2).
 */
public record LedgerLineResponse(String entryType, String accountNumber, BigDecimal amount, BigDecimal balanceBefore,
                                 BigDecimal balanceAfter, Instant createdAt) {
}
