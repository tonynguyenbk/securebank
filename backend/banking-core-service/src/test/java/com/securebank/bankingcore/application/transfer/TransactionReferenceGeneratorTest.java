package com.securebank.bankingcore.application.transfer;

import com.securebank.bankingcore.application.BusinessCalendar;
import com.securebank.bankingcore.config.BankingProperties;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TransactionReferenceGeneratorTest {

    @Test
    void formatsDateAndZeroPaddedCounter() {
        assertThat(TransactionReferenceGenerator.format(LocalDate.of(2026, 10, 8), 1)).isEqualTo("TX202610080001");
        assertThat(TransactionReferenceGenerator.format(LocalDate.of(2026, 10, 8), 9999)).isEqualTo("TX202610089999");
        assertThat(TransactionReferenceGenerator.format(LocalDate.of(2026, 10, 8), 10000))
                .isEqualTo("TX2026100810000");
        assertThatThrownBy(() -> TransactionReferenceGenerator.format(LocalDate.of(2026, 10, 8), 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void usesTheHoChiMinhBusinessDay() {
        // 2026-10-08T18:30Z is already 2026-10-09 01:30 in Asia/Ho_Chi_Minh (UTC+7)
        Clock clock = Clock.fixed(Instant.parse("2026-10-08T18:30:00Z"), ZoneOffset.UTC);
        BusinessCalendar calendar = new BusinessCalendar(clock, new BankingProperties(null, null, null, null, null));
        LocalDate[] seen = new LocalDate[1];
        AtomicLong counter = new AtomicLong();
        TransactionReferenceGenerator generator = new TransactionReferenceGenerator(date -> {
            seen[0] = date;
            return counter.incrementAndGet();
        }, calendar);

        assertThat(generator.next()).isEqualTo("TX202610090001");
        assertThat(generator.next()).isEqualTo("TX202610090002");
        assertThat(seen[0]).isEqualTo(LocalDate.of(2026, 10, 9));
    }
}
