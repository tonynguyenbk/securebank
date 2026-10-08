package com.securebank.bankingcore.application.transfer;

import com.securebank.common.error.ErrorCode;

import java.util.UUID;

/**
 * A business rule refused the transfer after the source account was identified (all 422 codes). Thrown
 * inside the transfer transaction so that it rolls back completely; {@link TransferService} then records
 * the REJECTED attempt in a separate transaction ({@link TransferRejectionRecorder}).
 */
public class TransferRejectedException extends RuntimeException {

    private final ErrorCode code;
    private final UUID sourceAccountId;
    private final UUID destinationAccountId;

    public TransferRejectedException(ErrorCode code, String message, UUID sourceAccountId,
                                     UUID destinationAccountId) {
        super(message, null, false, false);
        this.code = code;
        this.sourceAccountId = sourceAccountId;
        this.destinationAccountId = destinationAccountId;
    }

    public ErrorCode code() {
        return code;
    }

    public UUID sourceAccountId() {
        return sourceAccountId;
    }

    public UUID destinationAccountId() {
        return destinationAccountId;
    }
}
