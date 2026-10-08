package com.securebank.bankingcore.application.limits;

import com.securebank.bankingcore.api.TransferLimitsResponse;
import com.securebank.bankingcore.application.BusinessCalendar;
import com.securebank.bankingcore.config.BankingProperties;
import com.securebank.bankingcore.domain.Account;
import com.securebank.bankingcore.domain.Money;
import com.securebank.bankingcore.domain.TransactionStatus;
import com.securebank.bankingcore.domain.TransferLimit;
import com.securebank.bankingcore.repository.BankTransactionRepository;
import com.securebank.bankingcore.repository.TransferLimitRepository;
import com.securebank.common.error.ErrorCode;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * Per-transaction and daily outgoing limits (spec §14). "Used today" is the sum of today's SUCCESS outgoing
 * transfers (Asia/Ho_Chi_Minh day). During a transfer {@link #check} is called while the source account's
 * write lock is held, which makes the read-check-debit sequence race-free: every other outgoing transfer of
 * that account must wait for the lock, so it cannot commit between our sum and our debit.
 */
@Service
public class TransferLimitService {

    private final TransferLimitRepository limits;
    private final BankTransactionRepository transactions;
    private final BusinessCalendar calendar;
    private final BankingProperties properties;

    public TransferLimitService(TransferLimitRepository limits, BankTransactionRepository transactions,
                                BusinessCalendar calendar, BankingProperties properties) {
        this.limits = limits;
        this.transactions = transactions;
        this.calendar = calendar;
        this.properties = properties;
    }

    /** Empty when the amount is allowed; otherwise the first violated limit (per-transaction before daily). */
    public Optional<LimitViolation> check(UUID accountId, BigDecimal amount) {
        TransferLimit limit = require(accountId);
        if (amount.compareTo(limit.getPerTransactionLimit()) > 0) {
            return Optional.of(new LimitViolation(ErrorCode.TRANSFER_LIMIT_EXCEEDED,
                    "The amount exceeds the per-transaction limit of " + vnd(limit.getPerTransactionLimit()) + "."));
        }
        BigDecimal used = usedToday(accountId);
        if (used.add(amount).compareTo(limit.getDailyLimit()) > 0) {
            BigDecimal remaining = limit.getDailyLimit().subtract(used).max(Money.zero());
            return Optional.of(new LimitViolation(ErrorCode.DAILY_LIMIT_EXCEEDED,
                    "The amount exceeds the daily transfer limit of " + vnd(limit.getDailyLimit())
                            + " (remaining today: " + vnd(remaining) + ")."));
        }
        return Optional.empty();
    }

    public BigDecimal usedToday(UUID accountId) {
        BusinessCalendar.Range today = calendar.day(calendar.today());
        return Money.normalize(transactions.sumOutgoing(accountId, TransactionStatus.SUCCESS, today.from(), today.to()));
    }

    public TransferLimitsResponse usage(UUID accountId) {
        return usage(require(accountId));
    }

    public TransferLimitsResponse usage(TransferLimit limit) {
        BigDecimal used = usedToday(limit.getAccount().getId());
        BigDecimal remaining = limit.getDailyLimit().subtract(used).max(Money.zero());
        return new TransferLimitsResponse(limit.getPerTransactionLimit(), limit.getDailyLimit(), used, remaining,
                limit.getUpdatedAt());
    }

    /** Default limits for a newly opened account (demo defaults: 100M per transaction, 500M per day). */
    public TransferLimit openDefault(Account account) {
        return limits.save(new TransferLimit(UUID.randomUUID(), account, properties.defaultPerTransactionLimit(),
                properties.defaultDailyLimit(), calendar.now()));
    }

    private TransferLimit require(UUID accountId) {
        // Every account gets limits when it is opened; a missing row is a data error, not a client error.
        return limits.findByAccountId(accountId)
                .orElseThrow(() -> new IllegalStateException("No transfer limits configured for account " + accountId));
    }

    private static String vnd(BigDecimal amount) {
        DecimalFormat format = new DecimalFormat("#,##0.##", DecimalFormatSymbols.getInstance(Locale.US));
        return format.format(amount) + " VND";
    }
}
