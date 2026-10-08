package com.securebank.bankingcore.application.transfer;

import com.securebank.bankingcore.application.limits.LimitViolation;
import com.securebank.bankingcore.application.limits.TransferLimitService;
import com.securebank.bankingcore.domain.Account;
import com.securebank.bankingcore.domain.AccountStatus;
import com.securebank.common.error.ErrorCode;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Business rules evaluated on the <em>locked</em> accounts, in the contract's order (§2):
 * source FROZEN → source/destination CLOSED → currency mismatch → per-transaction limit → daily limit →
 * insufficient funds. A frozen destination may still receive money (documented policy, spec §16).
 * Every violation is a {@link TransferRejectedException} (HTTP 422, persisted as a REJECTED transaction).
 */
@Component
public class TransferPolicy {

    private final TransferLimitService limits;

    public TransferPolicy(TransferLimitService limits) {
        this.limits = limits;
    }

    public void check(Account source, Account destination, TransferCommand command) {
        if (source.getStatus() == AccountStatus.FROZEN) {
            throw reject(ErrorCode.ACCOUNT_FROZEN, "The source account is frozen and cannot send money.",
                    source, destination);
        }
        if (source.getStatus() == AccountStatus.CLOSED) {
            throw reject(ErrorCode.ACCOUNT_CLOSED, "The source account is closed.", source, destination);
        }
        if (destination.getStatus() == AccountStatus.CLOSED) {
            throw reject(ErrorCode.ACCOUNT_CLOSED, "The destination account is closed.", source, destination);
        }
        if (!source.getCurrency().equals(command.currency())
                || !destination.getCurrency().equals(source.getCurrency())) {
            throw reject(ErrorCode.CURRENCY_MISMATCH, ErrorCode.CURRENCY_MISMATCH.defaultMessage(), source,
                    destination);
        }
        Optional<LimitViolation> violation = limits.check(source.getId(), command.amount());
        if (violation.isPresent()) {
            throw reject(violation.get().code(), violation.get().message(), source, destination);
        }
        if (!source.hasSufficientFunds(command.amount())) {
            throw reject(ErrorCode.INSUFFICIENT_FUNDS, "The source account balance is insufficient for this transfer.",
                    source, destination);
        }
    }

    private static TransferRejectedException reject(ErrorCode code, String message, Account source,
                                                    Account destination) {
        return new TransferRejectedException(code, message, source.getId(), destination.getId());
    }
}
