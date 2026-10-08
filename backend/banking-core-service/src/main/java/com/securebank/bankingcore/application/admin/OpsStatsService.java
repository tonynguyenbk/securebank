package com.securebank.bankingcore.application.admin;

import com.securebank.bankingcore.api.DailyStatResponse;
import com.securebank.bankingcore.api.OpsStatsTodayResponse;
import com.securebank.bankingcore.application.BusinessCalendar;
import com.securebank.bankingcore.domain.AccountStatus;
import com.securebank.bankingcore.domain.Money;
import com.securebank.bankingcore.domain.TransactionStatus;
import com.securebank.bankingcore.repository.AccountRepository;
import com.securebank.bankingcore.repository.BankTransactionRepository;
import com.securebank.common.error.ApiException;
import com.securebank.common.error.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Dashboard numbers for the bank operations portal (contract §3, Asia/Ho_Chi_Minh days). */
@Service
@Transactional(readOnly = true)
public class OpsStatsService {

    private final BankTransactionRepository transactions;
    private final AccountRepository accounts;
    private final BusinessCalendar calendar;

    public OpsStatsService(BankTransactionRepository transactions, AccountRepository accounts,
                           BusinessCalendar calendar) {
        this.transactions = transactions;
        this.accounts = accounts;
        this.calendar = calendar;
    }

    public OpsStatsTodayResponse today() {
        LocalDate today = calendar.today();
        BusinessCalendar.Range range = calendar.day(today);
        long all = transactions.countCreatedBetween(range.from(), range.to());
        long success = transactions.countByStatusInCreatedBetween(Set.of(TransactionStatus.SUCCESS), range.from(),
                range.to());
        long failed = transactions.countByStatusInCreatedBetween(
                Set.of(TransactionStatus.FAILED, TransactionStatus.REJECTED), range.from(), range.to());
        BigDecimal total = Money.normalize(transactions.sumByStatusCreatedBetween(TransactionStatus.SUCCESS,
                range.from(), range.to()));
        return new OpsStatsTodayResponse(today, all, success, failed, total,
                accounts.countByStatus(AccountStatus.FROZEN), Money.VND);
    }

    /** One row per day for the last {@code days} days including today, oldest first; empty days are zero. */
    public List<DailyStatResponse> daily(int days) {
        if (days < 1 || days > 90) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "days must be between 1 and 90");
        }
        LocalDate today = calendar.today();
        LocalDate first = today.minusDays(days - 1L);
        BusinessCalendar.Range range = calendar.between(first, today);
        Map<LocalDate, DailyStatResponse> byDay = new HashMap<>();
        for (Object[] row : transactions.dailySuccessTotals(calendar.zone().getId(), range.from(), range.to())) {
            LocalDate date = toLocalDate(row[0]);
            byDay.put(date, new DailyStatResponse(date, ((Number) row[1]).longValue(),
                    Money.normalize(new BigDecimal(row[2].toString()))));
        }
        List<DailyStatResponse> result = new ArrayList<>(days);
        for (LocalDate d = first; !d.isAfter(today); d = d.plusDays(1)) {
            result.add(byDay.getOrDefault(d, new DailyStatResponse(d, 0, Money.zero())));
        }
        return result;
    }

    private static LocalDate toLocalDate(Object value) {
        if (value instanceof LocalDate date) {
            return date;
        }
        if (value instanceof Date sqlDate) {
            return sqlDate.toLocalDate();
        }
        return LocalDate.parse(value.toString());
    }
}
