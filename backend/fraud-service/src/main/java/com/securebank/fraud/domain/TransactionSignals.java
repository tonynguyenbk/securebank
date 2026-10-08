package com.securebank.fraud.domain;

import java.math.BigDecimal;

/**
 * Behavioural signals computed for one transaction (by the Redis-backed {@code FraudSignals} adapter).
 * All values already include the transaction being evaluated.
 *
 * @param outgoingTransfersInWindow  outgoing transfers by the customer inside the frequency window
 * @param outgoingTotalToday         customer's outgoing total for the business day (Asia/Ho_Chi_Minh)
 * @param firstTransferToBeneficiary true when the customer never sent money to the destination account before
 */
public record TransactionSignals(int outgoingTransfersInWindow, BigDecimal outgoingTotalToday,
                                 boolean firstTransferToBeneficiary) {
}
