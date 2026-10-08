package com.securebank.bankingcore.application.transfer;

import java.math.BigDecimal;

/**
 * A validated, normalized transfer request: amount at scale 2, upper-case currency, trimmed description
 * (null when blank). Hashing and execution both work on this form, so "1000000" and "1000000.00" are the
 * same request.
 */
public record TransferCommand(String sourceAccountNumber, String destinationAccountNumber, BigDecimal amount,
                              String currency, String description) {
}
