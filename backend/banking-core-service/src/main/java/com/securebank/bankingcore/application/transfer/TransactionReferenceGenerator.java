package com.securebank.bankingcore.application.transfer;

import com.securebank.bankingcore.application.BusinessCalendar;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * Readable references {@code TX} + yyyyMMdd (business day, Asia/Ho_Chi_Minh) + per-day counter zero-padded
 * to 4 digits, e.g. {@code TX202610080001} (spec §31). The counter grows past 4 digits if needed
 * (TX2026100810000) — still unique because the date part is fixed-width. The UUID stays the internal key.
 */
@Component
public class TransactionReferenceGenerator {

    private static final DateTimeFormatter DATE = DateTimeFormatter.BASIC_ISO_DATE;

    private final ReferenceCounter counter;
    private final BusinessCalendar calendar;

    public TransactionReferenceGenerator(ReferenceCounter counter, BusinessCalendar calendar) {
        this.counter = counter;
        this.calendar = calendar;
    }

    public String next() {
        LocalDate today = calendar.today();
        return format(today, counter.next(today));
    }

    static String format(LocalDate date, long sequence) {
        if (sequence <= 0) {
            throw new IllegalArgumentException("sequence must be positive");
        }
        return "TX" + DATE.format(date) + String.format("%04d", sequence);
    }
}
