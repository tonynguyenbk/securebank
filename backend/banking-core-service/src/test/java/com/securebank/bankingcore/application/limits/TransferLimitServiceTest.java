package com.securebank.bankingcore.application.limits;

import com.securebank.bankingcore.application.BusinessCalendar;
import com.securebank.bankingcore.config.BankingProperties;
import com.securebank.bankingcore.domain.Account;
import com.securebank.bankingcore.domain.Customer;
import com.securebank.bankingcore.domain.TransactionStatus;
import com.securebank.bankingcore.domain.TransferLimit;
import com.securebank.bankingcore.repository.BankTransactionRepository;
import com.securebank.bankingcore.repository.TransferLimitRepository;
import com.securebank.common.error.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TransferLimitServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-08T03:00:00Z"); // 10:00 in Ho Chi Minh

    @Mock
    private TransferLimitRepository limits;
    @Mock
    private BankTransactionRepository transactions;

    private TransferLimitService service;
    private Account account;

    @BeforeEach
    void setUp() {
        BusinessCalendar calendar = new BusinessCalendar(Clock.fixed(NOW, ZoneOffset.UTC),
                new BankingProperties(null, null, null, null, null));
        service = new TransferLimitService(limits, transactions, calendar,
                new BankingProperties(null, null, null, null, null));
        Customer customer = new Customer(UUID.randomUUID(), UUID.randomUUID(), "A", "a@x", null, NOW);
        account = new Account(UUID.randomUUID(), customer, "1000000101", "VND", BigDecimal.ZERO, NOW);
        TransferLimit limit = new TransferLimit(UUID.randomUUID(), account, new BigDecimal("1000"),
                new BigDecimal("5000"), NOW);
        when(limits.findByAccountId(account.getId())).thenReturn(Optional.of(limit));
    }

    @Test
    void amountWithinBothLimitsIsAllowed() {
        usedToday("3999.99");
        assertThat(service.check(account.getId(), new BigDecimal("1000.00"))).isEmpty();
    }

    @Test
    void perTransactionLimitIsCheckedFirst() {
        assertThat(service.check(account.getId(), new BigDecimal("1000.01")))
                .get().extracting(LimitViolation::code).isEqualTo(ErrorCode.TRANSFER_LIMIT_EXCEEDED);
    }

    @Test
    void dailyLimitCountsTodaysSuccessfulOutgoingTransfers() {
        usedToday("4500.00");
        assertThat(service.check(account.getId(), new BigDecimal("500.01")))
                .get().extracting(LimitViolation::code).isEqualTo(ErrorCode.DAILY_LIMIT_EXCEEDED);
        assertThat(service.check(account.getId(), new BigDecimal("500.00"))).isEmpty();
    }

    @Test
    void todayIsTheHoChiMinhCalendarDay() {
        usedToday("0");
        service.check(account.getId(), BigDecimal.ONE);
        // Ho Chi Minh 2026-10-08 00:00 = 2026-10-07T17:00Z, until 2026-10-08T17:00Z
        verify(transactions).sumOutgoing(account.getId(), TransactionStatus.SUCCESS,
                Instant.parse("2026-10-07T17:00:00Z"), Instant.parse("2026-10-08T17:00:00Z"));
    }

    @Test
    void usageReportsRemainingNeverBelowZero() {
        usedToday("4800.00");
        var usage = service.usage(account.getId());
        assertThat(usage.usedToday()).isEqualByComparingTo("4800");
        assertThat(usage.remainingToday()).isEqualByComparingTo("200");
    }

    private void usedToday(String amount) {
        when(transactions.sumOutgoing(eq(account.getId()), eq(TransactionStatus.SUCCESS), any(), any()))
                .thenReturn(new BigDecimal(amount));
    }
}
