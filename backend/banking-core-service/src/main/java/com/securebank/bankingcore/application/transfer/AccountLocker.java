package com.securebank.bankingcore.application.transfer;

import com.securebank.bankingcore.domain.Account;
import com.securebank.bankingcore.repository.AccountRepository;
import com.securebank.common.error.ApiException;
import com.securebank.common.error.ErrorCode;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.UUID;

/**
 * Row locks for money movement (spec §11).
 *
 * <p><b>Deadlock avoidance:</b> two transfers A→B and B→A running at the same time would deadlock if each
 * locked its own source first. Every transaction in this service therefore locks accounts in one global
 * order — ascending UUID, compared as canonical lower-case strings (the same order PostgreSQL uses for
 * {@code uuid}). Lock waits are bounded by the transaction's {@code lock_timeout}; a timeout is reported as
 * a retryable error and the whole transfer rolls back.
 */
@Component
public class AccountLocker {

    /** Unsigned, byte-wise UUID order (matches PostgreSQL); {@link UUID#compareTo} is signed. */
    static final Comparator<UUID> LOCK_ORDER = Comparator.comparing(UUID::toString);

    private final AccountRepository accounts;
    private final JdbcTemplate jdbc;

    public AccountLocker(AccountRepository accounts, JdbcTemplate jdbc) {
        this.accounts = accounts;
        this.jdbc = jdbc;
    }

    /** {@code SELECT ... FOR UPDATE} both accounts, in deterministic order. */
    @Transactional(propagation = Propagation.MANDATORY)
    public LockedPair lockForTransfer(UUID sourceId, UUID destinationId) {
        boolean sourceFirst = LOCK_ORDER.compare(sourceId, destinationId) <= 0;
        UUID first = sourceFirst ? sourceId : destinationId;
        UUID second = sourceFirst ? destinationId : sourceId;
        Account firstLocked = lock(first);
        Account secondLocked = lock(second);
        return sourceFirst ? new LockedPair(firstLocked, secondLocked) : new LockedPair(secondLocked, firstLocked);
    }

    /**
     * {@code FOR KEY SHARE} in the same global order. Used when only inserting rows that reference the
     * accounts (a REJECTED transaction): the foreign-key check would take these locks implicitly at flush time,
     * i.e. after the reference counter; taking them explicitly first keeps the lock order identical to the
     * transfer path (accounts → counter) and therefore deadlock-free.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void keyShare(UUID a, UUID b) {
        if (b == null || a.equals(b)) {
            keyShare(a);
            return;
        }
        boolean aFirst = LOCK_ORDER.compare(a, b) <= 0;
        keyShare(aFirst ? a : b);
        keyShare(aFirst ? b : a);
    }

    private void keyShare(UUID id) {
        try {
            jdbc.queryForList("SELECT id FROM accounts WHERE id = ? FOR KEY SHARE", id);
        } catch (PessimisticLockingFailureException e) {
            throw busy();
        }
    }

    private Account lock(UUID id) {
        try {
            return accounts.findByIdForUpdate(id).orElseThrow(() -> new ApiException(ErrorCode.ACCOUNT_NOT_FOUND));
        } catch (PessimisticLockingFailureException e) {
            throw busy();
        }
    }

    private static ApiException busy() {
        return new ApiException(ErrorCode.ACCOUNT_BUSY,
                "The account is busy with another transfer. Please retry with the same Idempotency-Key.");
    }

    public record LockedPair(Account source, Account destination) {
    }
}
