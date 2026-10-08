package com.securebank.bankingcore.application.transfer;

import com.securebank.bankingcore.application.BusinessCalendar;
import com.securebank.bankingcore.config.BankingProperties;
import com.securebank.bankingcore.domain.IdempotencyRecord;
import com.securebank.bankingcore.repository.IdempotencyRecordRepository;
import com.securebank.common.error.ApiException;
import com.securebank.common.error.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Idempotency for POST /transfers (spec §12, contract §2), scoped to (userId, Idempotency-Key).
 *
 * <h2>Why duplicates can never move money twice</h2>
 * <ol>
 *   <li><b>Fast path</b> ({@link #findReplay}): a retry of a finished request finds the stored response and
 *       returns it without opening a write transaction.</li>
 *   <li><b>Claim inside the money transaction</b> ({@link #claim}): the very first statement of the transfer
 *       transaction is {@code INSERT ... ON CONFLICT DO NOTHING} into {@code idempotency_records}. The unique
 *       index on (user_id, idempotency_key) is the serialization point: if two requests with the same key run
 *       concurrently, the second INSERT <em>blocks</em> on the first one's uncommitted index entry.
 *       <ul>
 *         <li>first commits → the second's INSERT reports a conflict, it reads the committed row and
 *             replays the stored response (or 409 IDEMPOTENCY_KEY_CONFLICT if the payload differs);</li>
 *         <li>first rolls back (e.g. 404 / technical error) → nothing was stored and the second simply
 *             proceeds as the owner of the key;</li>
 *         <li>first is still running after {@code lock_timeout} → 409 IDEMPOTENCY_REQUEST_IN_PROGRESS.</li>
 *       </ul></li>
 *   <li>The claimed row is completed with the response code/body <em>in the same transaction</em> as the debit,
 *       credit, ledger and outbox rows, so other transactions only ever see completed rows and the stored
 *       response always matches what actually happened in the database.</li>
 * </ol>
 * Because the claim precedes the account locks, a waiting duplicate holds no account lock (no deadlock).
 */
@Service
public class IdempotencyService {

    private static final Logger log = LoggerFactory.getLogger(IdempotencyService.class);

    private final IdempotencyRecordRepository records;
    private final BusinessCalendar calendar;
    private final BankingProperties properties;

    public IdempotencyService(IdempotencyRecordRepository records, BusinessCalendar calendar,
                              BankingProperties properties) {
        this.records = records;
        this.calendar = calendar;
        this.properties = properties;
    }

    /** Fast path: the stored response of a finished request with this key, if any. */
    @Transactional(readOnly = true)
    public Optional<TransferResult> findReplay(UUID userId, String key, String requestHash) {
        return records.findByUserIdAndIdempotencyKey(userId, key).map(record -> replayOf(record, requestHash));
    }

    /** Claims the key for the current transaction, or returns the stored response of the committed owner. */
    @Transactional(propagation = Propagation.MANDATORY)
    public Claim claim(UUID userId, String key, String requestHash) {
        Instant now = calendar.now();
        int inserted;
        try {
            inserted = records.claim(UUID.randomUUID(), userId, key, requestHash, now,
                    now.plus(properties.idempotencyRetention()));
        } catch (PessimisticLockingFailureException e) {
            // another request with the same key still holds its uncommitted claim
            throw new ApiException(ErrorCode.IDEMPOTENCY_REQUEST_IN_PROGRESS);
        }
        IdempotencyRecord record = records.findByUserIdAndIdempotencyKey(userId, key)
                .orElseThrow(() -> new IllegalStateException("Idempotency record vanished after claim"));
        if (inserted == 1) {
            return new Claim(record, null);
        }
        return new Claim(null, replayOf(record, requestHash));
    }

    private TransferResult replayOf(IdempotencyRecord record, String requestHash) {
        if (!record.matches(requestHash)) {
            throw new ApiException(ErrorCode.IDEMPOTENCY_KEY_CONFLICT);
        }
        if (!record.isCompleted()) {
            // cannot normally happen: rows only become visible together with their response
            throw new ApiException(ErrorCode.IDEMPOTENCY_REQUEST_IN_PROGRESS);
        }
        return new TransferResult(record.getResponseCode(), record.getResponseBody(), true,
                record.getTransactionId());
    }

    /** Expired keys may be reused; stored responses are kept for {@code idempotency-retention}. */
    @Scheduled(fixedDelayString = "${securebank.banking.idempotency-cleanup-interval:PT1H}",
            initialDelayString = "${securebank.banking.idempotency-cleanup-interval:PT1H}")
    @Transactional
    public void purgeExpired() {
        int deleted = records.deleteExpired(calendar.now());
        if (deleted > 0) {
            log.info("Purged {} expired idempotency records", deleted);
        }
    }

    /** Exactly one of {@code owned} (this transaction owns the key) or {@code replay} is set. */
    public record Claim(IdempotencyRecord owned, TransferResult replay) {

        public boolean isOwner() {
            return owned != null;
        }
    }
}
