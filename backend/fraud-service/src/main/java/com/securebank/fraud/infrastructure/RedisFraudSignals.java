package com.securebank.fraud.infrastructure;

import com.securebank.fraud.application.FraudSignals;
import com.securebank.fraud.config.FraudProperties;
import com.securebank.fraud.domain.TransactionSignals;
import com.securebank.fraud.repository.KnownBeneficiaryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Date;
import java.util.List;
import java.util.UUID;

/**
 * Redis-backed behavioural signals.
 *
 * <ul>
 *   <li><b>Frequency</b> — sorted set {@code fraud:freq:<customerId>}, member = transactionId, score = event time
 *       (epoch ms). Entries older than the window are pruned; the count inside the window is the signal.</li>
 *   <li><b>Daily velocity</b> — hash {@code fraud:daily:<customerId>:<yyyy-MM-dd>} (business day in
 *       Asia/Ho_Chi_Minh), field = transactionId, value = amount. The key expires at the end of that day.</li>
 *   <li><b>New beneficiary</b> — set {@code fraud:beneficiaries:<customerId>} of destination account ids, a cache of
 *       the {@code known_beneficiaries} table. A Redis miss falls through to the table (the source of truth), so a
 *       flushed Redis never turns a known beneficiary into a "new" one.</li>
 * </ul>
 *
 * <p><b>Transactions:</b> Redis writes are <i>not</i> rolled back with the database transaction. Two measures keep
 * that safe: (1) every write is keyed by transactionId (ZADD / HSET are idempotent), so a consumer retry after a
 * rollback does not double-count; (2) the beneficiary cache is only filled <i>after commit</i>, because a cached entry
 * whose DB row was rolled back would hide a genuinely new beneficiary on retry. The remaining trade-off: if an event is
 * finally skipped as poison after its counters were written, those counters still include it until they expire
 * (≤ 1 day). For a risk signal that errs towards more scrutiny, which is acceptable. Redis is never the source of truth
 * for money.
 */
@Component
public class RedisFraudSignals implements FraudSignals {

    private static final Logger log = LoggerFactory.getLogger(RedisFraudSignals.class);

    static final String FREQ_PREFIX = "fraud:freq:";
    static final String DAILY_PREFIX = "fraud:daily:";
    static final String BENEFICIARY_PREFIX = "fraud:beneficiaries:";

    private final StringRedisTemplate redis;
    private final KnownBeneficiaryRepository beneficiaries;
    private final FraudProperties properties;
    private final Clock clock;

    public RedisFraudSignals(StringRedisTemplate redis, KnownBeneficiaryRepository beneficiaries,
                             FraudProperties properties, Clock clock) {
        this.redis = redis;
        this.beneficiaries = beneficiaries;
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    public TransactionSignals recordAndCollect(OutgoingTransfer t) {
        int inWindow = recordFrequency(t);
        BigDecimal today = recordDailyTotal(t);
        boolean first = firstTransferToBeneficiary(t);
        return new TransactionSignals(inWindow, today, first);
    }

    private int recordFrequency(OutgoingTransfer t) {
        String key = FREQ_PREFIX + t.customerId();
        Duration window = properties.highFrequencyWindow();
        long at = t.occurredAt().toEpochMilli();
        long windowStart = at - window.toMillis();
        var zset = redis.opsForZSet();
        zset.add(key, t.transactionId().toString(), at);
        zset.removeRangeByScore(key, Double.NEGATIVE_INFINITY, windowStart);
        redis.expire(key, window.multipliedBy(2));
        Long count = zset.count(key, windowStart + 1, at);
        return count == null ? 1 : count.intValue();
    }

    private BigDecimal recordDailyTotal(OutgoingTransfer t) {
        LocalDate day = t.occurredAt().atZone(properties.businessZone()).toLocalDate();
        String key = DAILY_PREFIX + t.customerId() + ":" + day;
        redis.opsForHash().put(key, t.transactionId().toString(), t.amount().toPlainString());
        Instant endOfDay = day.plusDays(1).atStartOfDay(properties.businessZone()).toInstant();
        Instant minimumExpiry = clock.instant().plusSeconds(60);
        redis.expireAt(key, Date.from(endOfDay.isAfter(minimumExpiry) ? endOfDay : minimumExpiry));
        List<Object> amounts = redis.opsForHash().values(key);
        BigDecimal total = BigDecimal.ZERO;
        for (Object amount : amounts) {
            total = total.add(new BigDecimal(String.valueOf(amount)));
        }
        return total;
    }

    private boolean firstTransferToBeneficiary(OutgoingTransfer t) {
        if (t.destinationAccountId() == null) {
            return false;
        }
        String key = BENEFICIARY_PREFIX + t.customerId();
        String member = t.destinationAccountId().toString();
        if (Boolean.TRUE.equals(redis.opsForSet().isMember(key, member))) {
            return false;
        }
        boolean first = beneficiaries.registerIfAbsent(t.customerId(), t.destinationAccountId(), t.occurredAt());
        cacheAfterCommit(key, member);
        return first;
    }

    private void cacheAfterCommit(String key, String member) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    try {
                        redis.opsForSet().add(key, member);
                    } catch (RuntimeException e) {
                        // harmless: the next lookup misses the cache and reads known_beneficiaries
                        log.warn("Could not cache known beneficiary in Redis: {}", e.toString());
                    }
                }
            });
        } else {
            redis.opsForSet().add(key, member);
        }
    }
}
