package com.securebank.bankingcore.domain;

/**
 * SUCCESS = money moved (two ledger lines). REJECTED = a business rule refused the transfer (no ledger lines).
 * PENDING and FAILED are part of the contract for future asynchronous flows; v1 transfers are synchronous
 * and atomic, so they are never persisted in those states (a technical failure rolls everything back).
 */
public enum TransactionStatus {
    PENDING,
    SUCCESS,
    FAILED,
    REJECTED
}
