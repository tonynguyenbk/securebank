package com.securebank.bankingcore.application;

import com.securebank.bankingcore.config.BankingProperties;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

/**
 * "Today" and date filters in the bank's business zone (Asia/Ho_Chi_Minh), converted to UTC instant ranges
 * for querying TIMESTAMPTZ columns. All ranges are half-open: [from, to).
 */
@Component
public class BusinessCalendar {

    /** Lower/upper bounds used instead of NULL parameters for open-ended ranges. */
    public static final Instant MIN = Instant.parse("1970-01-01T00:00:00Z");
    public static final Instant MAX = Instant.parse("9999-01-01T00:00:00Z");

    private final Clock clock;
    private final ZoneId zone;

    public BusinessCalendar(Clock clock, BankingProperties properties) {
        this.clock = clock;
        this.zone = properties.businessZone();
    }

    public Instant now() {
        return clock.instant();
    }

    public ZoneId zone() {
        return zone;
    }

    public LocalDate today() {
        return LocalDate.now(clock.withZone(zone));
    }

    public LocalDate dateOf(Instant instant) {
        return instant.atZone(zone).toLocalDate();
    }

    public Instant startOf(LocalDate date) {
        return date.atStartOfDay(zone).toInstant();
    }

    public Range day(LocalDate date) {
        return new Range(startOf(date), startOf(date.plusDays(1)));
    }

    /** Inclusive date filter (fromDate..toDate, either may be null) as an instant range. */
    public Range between(LocalDate fromDate, LocalDate toDate) {
        Instant from = fromDate == null ? MIN : startOf(fromDate);
        Instant to = toDate == null ? MAX : startOf(toDate.plusDays(1));
        return new Range(from, to);
    }

    public record Range(Instant from, Instant to) {
    }
}
