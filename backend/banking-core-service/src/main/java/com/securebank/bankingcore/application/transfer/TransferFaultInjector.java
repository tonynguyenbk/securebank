package com.securebank.bankingcore.application.transfer;

import java.util.UUID;

/**
 * Test seam for the rollback guarantee (spec §36 "Transaction rollback"): called inside the transfer
 * transaction right after the source has been debited and before the destination is credited. The production
 * bean is {@link #NONE}; integration tests replace it with one that throws, then assert that nothing was
 * persisted.
 */
@FunctionalInterface
public interface TransferFaultInjector {

    TransferFaultInjector NONE = sourceAccountId -> {
    };

    void afterDebit(UUID sourceAccountId);
}
