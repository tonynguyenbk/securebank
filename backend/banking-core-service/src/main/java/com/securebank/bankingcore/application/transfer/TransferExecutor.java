package com.securebank.bankingcore.application.transfer;

import com.securebank.bankingcore.application.BusinessCalendar;
import com.securebank.bankingcore.application.audit.Actor;
import com.securebank.bankingcore.application.view.TransactionViewMapper;
import com.securebank.bankingcore.domain.Account;
import com.securebank.bankingcore.domain.BankTransaction;
import com.securebank.bankingcore.domain.LedgerEntry;
import com.securebank.bankingcore.infrastructure.persistence.TransactionLockSettings;
import com.securebank.bankingcore.repository.AccountRef;
import com.securebank.bankingcore.repository.AccountRepository;
import com.securebank.bankingcore.repository.BankTransactionRepository;
import com.securebank.bankingcore.security.OwnershipGuard;
import com.securebank.common.error.ApiException;
import com.securebank.common.error.ErrorCode;
import com.securebank.common.json.EventJson;
import com.securebank.common.security.AuthenticatedUser;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * The money-moving transaction (spec §10). Everything below commits or rolls back as ONE PostgreSQL
 * transaction: idempotency claim, account locks, debit, credit, SUCCESS transaction row, two ledger entries,
 * stored idempotency response, TRANSACTION_COMPLETED + audit outbox rows.
 *
 * <ol>
 *   <li>bound lock waits ({@code SET LOCAL lock_timeout});</li>
 *   <li>claim the Idempotency-Key (may replay a committed duplicate instead, see {@link IdempotencyService});</li>
 *   <li>resolve account numbers to IDs without loading entities; 404 / 403 ownership from the JWT subject;</li>
 *   <li>{@code SELECT ... FOR UPDATE} both accounts in deterministic UUID order ({@link AccountLocker});</li>
 *   <li>business rules on the locked rows ({@link TransferPolicy}) — a violation throws
 *       {@link TransferRejectedException} and the whole transaction rolls back;</li>
 *   <li>debit, [fault-injection seam], credit, reference, transaction row, ledger, events, idempotency response.</li>
 * </ol>
 */
@Service
public class TransferExecutor {

    private final TransactionLockSettings lockSettings;
    private final IdempotencyService idempotency;
    private final AccountRepository accounts;
    private final AccountLocker locker;
    private final OwnershipGuard ownership;
    private final TransferPolicy policy;
    private final TransferFaultInjector faultInjector;
    private final TransactionReferenceGenerator references;
    private final BankTransactionRepository transactions;
    private final LedgerPoster ledger;
    private final TransferEvents events;
    private final TransactionViewMapper views;
    private final EventJson json;
    private final BusinessCalendar calendar;

    public TransferExecutor(TransactionLockSettings lockSettings, IdempotencyService idempotency,
                            AccountRepository accounts, AccountLocker locker, OwnershipGuard ownership,
                            TransferPolicy policy, TransferFaultInjector faultInjector,
                            TransactionReferenceGenerator references, BankTransactionRepository transactions,
                            LedgerPoster ledger, TransferEvents events, TransactionViewMapper views, EventJson json,
                            BusinessCalendar calendar) {
        this.lockSettings = lockSettings;
        this.idempotency = idempotency;
        this.accounts = accounts;
        this.locker = locker;
        this.ownership = ownership;
        this.policy = policy;
        this.faultInjector = faultInjector;
        this.references = references;
        this.transactions = transactions;
        this.ledger = ledger;
        this.events = events;
        this.views = views;
        this.json = json;
        this.calendar = calendar;
    }

    @Transactional
    public TransferResult execute(AuthenticatedUser user, String idempotencyKey, String requestHash,
                                  TransferCommand command) {
        lockSettings.applyLockTimeout();

        IdempotencyService.Claim claim = idempotency.claim(user.userId(), idempotencyKey, requestHash);
        if (!claim.isOwner()) {
            return claim.replay();
        }

        AccountRef sourceRef = accounts.findRefByAccountNumber(command.sourceAccountNumber())
                .orElseThrow(() -> new ApiException(ErrorCode.ACCOUNT_NOT_FOUND, "The source account was not found."));
        ownership.requireOwner(user, sourceRef.ownerUserId());
        AccountRef destinationRef = accounts.findRefByAccountNumber(command.destinationAccountNumber())
                .orElseThrow(() -> new ApiException(ErrorCode.ACCOUNT_NOT_FOUND,
                        "The destination account was not found."));

        AccountLocker.LockedPair locked = locker.lockForTransfer(sourceRef.id(), destinationRef.id());
        Account source = locked.source();
        Account destination = locked.destination();

        policy.check(source, destination, command);

        Instant now = calendar.now();
        Account.BalanceChange debit = source.debit(command.amount(), now);
        faultInjector.afterDebit(source.getId());
        Account.BalanceChange credit = destination.credit(command.amount(), now);

        BankTransaction tx = transactions.save(BankTransaction.success(UUID.randomUUID(), references.next(), source,
                destination, command.amount(), command.currency(), command.description(), user.userId(), now));
        List<LedgerEntry> entries = ledger.post(tx, source, debit, destination, credit, now);

        Set<UUID> viewerAccounts = new HashSet<>();
        viewerAccounts.add(source.getId());
        if (ownership.isOwner(user.userId(), destinationRef.ownerUserId())) {
            viewerAccounts.add(destination.getId());
        }
        String body = json.write(views.toTransferResponse(tx, source, destination, entries, viewerAccounts));
        claim.owned().complete(tx.getId(), HttpStatus.CREATED.value(), body, now);

        events.completed(tx, source, destination, Actor.of(user));
        return new TransferResult(HttpStatus.CREATED.value(), body, false, tx.getId());
    }
}
