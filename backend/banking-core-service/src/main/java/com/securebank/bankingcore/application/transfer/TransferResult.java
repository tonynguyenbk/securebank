package com.securebank.bankingcore.application.transfer;

import java.util.UUID;

/**
 * The HTTP outcome of POST /transfers as stored in the idempotency record: status code + exact JSON body.
 * A fresh request and every replay of it return the same bytes; {@code replayed} drives the
 * {@code Idempotent-Replayed: true} header.
 */
public record TransferResult(int status, String body, boolean replayed, UUID transactionId) {

    public TransferResult asReplay() {
        return new TransferResult(status, body, true, transactionId);
    }
}
