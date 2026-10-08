package com.securebank.bankingcore.application.transfer;

import com.securebank.bankingcore.application.BusinessCalendar;
import com.securebank.bankingcore.application.audit.Actor;
import com.securebank.bankingcore.domain.Account;
import com.securebank.bankingcore.domain.BankTransaction;
import com.securebank.bankingcore.infrastructure.persistence.TransactionLockSettings;
import com.securebank.bankingcore.repository.AccountRepository;
import com.securebank.bankingcore.repository.BankTransactionRepository;
import com.securebank.common.error.ApiError;
import com.securebank.common.json.EventJson;
import com.securebank.common.security.AuthenticatedUser;
import com.securebank.common.web.CorrelationId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Records a business rejection <em>after</em> the transfer transaction has rolled back, in a new transaction:
 * a REJECTED bank_transaction (no ledger lines — no money moved), the idempotency record holding the 422
 * response (so a retry gets the same rejection), TransactionFailedEvent and audit TRANSFER_REJECTED.
 *
 * <p>It claims the Idempotency-Key exactly like the transfer path. If a concurrent request with the same key
 * committed first, that outcome wins and is replayed — so one key never maps to two different results.
 */
@Service
public class TransferRejectionRecorder {

    static final String TRANSFERS_PATH = "/api/v1/transfers";

    private final TransactionLockSettings lockSettings;
    private final IdempotencyService idempotency;
    private final AccountRepository accounts;
    private final AccountLocker locker;
    private final TransactionReferenceGenerator references;
    private final BankTransactionRepository transactions;
    private final TransferEvents events;
    private final EventJson json;
    private final BusinessCalendar calendar;

    public TransferRejectionRecorder(TransactionLockSettings lockSettings, IdempotencyService idempotency,
                                     AccountRepository accounts, AccountLocker locker,
                                     TransactionReferenceGenerator references, BankTransactionRepository transactions,
                                     TransferEvents events, EventJson json, BusinessCalendar calendar) {
        this.lockSettings = lockSettings;
        this.idempotency = idempotency;
        this.accounts = accounts;
        this.locker = locker;
        this.references = references;
        this.transactions = transactions;
        this.events = events;
        this.json = json;
        this.calendar = calendar;
    }

    @Transactional
    public TransferResult record(AuthenticatedUser user, String idempotencyKey, String requestHash,
                                 TransferCommand command, TransferRejectedException rejection) {
        lockSettings.applyLockTimeout();
        IdempotencyService.Claim claim = idempotency.claim(user.userId(), idempotencyKey, requestHash);
        if (!claim.isOwner()) {
            return claim.replay();
        }
        // same lock order as the transfer path: accounts before the reference counter
        locker.keyShare(rejection.sourceAccountId(), rejection.destinationAccountId());
        Account source = accounts.findWithCustomerById(rejection.sourceAccountId())
                .orElseThrow(() -> new IllegalStateException("Rejected transfer source vanished"));
        Account destination = rejection.destinationAccountId() == null ? null
                : accounts.findById(rejection.destinationAccountId()).orElse(null);

        var now = calendar.now();
        BankTransaction tx = transactions.save(BankTransaction.rejected(UUID.randomUUID(), references.next(), source,
                destination, command.destinationAccountNumber(), command.amount(), command.currency(),
                command.description(), rejection.code().name(), truncate(rejection.getMessage()), user.userId(), now));

        ApiError error = new ApiError(now, rejection.code().status().value(), rejection.code().name(),
                rejection.getMessage(), TRANSFERS_PATH, CorrelationId.current(), List.of());
        String body = json.write(error);
        claim.owned().complete(tx.getId(), error.status(), body, now);

        events.rejected(tx, source, Actor.of(user));
        return new TransferResult(error.status(), body, false, tx.getId());
    }

    private static String truncate(String message) {
        return message == null || message.length() <= 255 ? message : message.substring(0, 255);
    }
}
