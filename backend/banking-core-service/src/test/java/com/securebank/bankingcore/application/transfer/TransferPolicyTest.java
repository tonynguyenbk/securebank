package com.securebank.bankingcore.application.transfer;

import com.securebank.bankingcore.application.limits.LimitViolation;
import com.securebank.bankingcore.application.limits.TransferLimitService;
import com.securebank.bankingcore.domain.Account;
import com.securebank.bankingcore.domain.AccountStatus;
import com.securebank.bankingcore.domain.Customer;
import com.securebank.common.error.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class TransferPolicyTest {

    @Mock
    private TransferLimitService limits;

    private TransferPolicy policy;
    private Account source;
    private Account destination;

    @BeforeEach
    void setUp() {
        policy = new TransferPolicy(limits);
        source = account("1000.00");
        destination = account("0.00");
        lenient().when(limits.check(any(), any())).thenReturn(Optional.empty());
    }

    @Test
    void allowsAValidTransfer() {
        assertThatCode(() -> policy.check(source, destination, command("1000.00"))).doesNotThrowAnyException();
    }

    @Test
    void frozenSourceIsRejectedBeforeAnythingElse() {
        source.changeStatus(AccountStatus.FROZEN, Instant.now());
        assertRejected(command("999999.00"), ErrorCode.ACCOUNT_FROZEN);
        verifyNoInteractions(limits);
    }

    @Test
    void frozenDestinationMayReceive() {
        destination.changeStatus(AccountStatus.FROZEN, Instant.now());
        assertThatCode(() -> policy.check(source, destination, command("10.00"))).doesNotThrowAnyException();
    }

    @Test
    void closedAccountsAreRejected() {
        destination.changeStatus(AccountStatus.CLOSED, Instant.now());
        assertRejected(command("10.00"), ErrorCode.ACCOUNT_CLOSED);
    }

    @Test
    void currencyMismatchIsRejected() {
        TransferCommand usd = new TransferCommand("1", "2", new BigDecimal("10.00"), "USD", null);
        assertRejected(usd, ErrorCode.CURRENCY_MISMATCH);
    }

    @Test
    void limitViolationIsRejectedBeforeInsufficientFunds() {
        lenient().when(limits.check(any(), any())).thenReturn(Optional.of(
                new LimitViolation(ErrorCode.DAILY_LIMIT_EXCEEDED, "daily")));
        assertRejected(command("5000.00"), ErrorCode.DAILY_LIMIT_EXCEEDED);
    }

    @Test
    void insufficientFundsIsRejected() {
        assertRejected(command("1000.01"), ErrorCode.INSUFFICIENT_FUNDS);
    }

    private void assertRejected(TransferCommand command, ErrorCode code) {
        assertThatThrownBy(() -> policy.check(source, destination, command))
                .isInstanceOfSatisfying(TransferRejectedException.class, e -> {
                    assertThat(e.code()).isEqualTo(code);
                    assertThat(e.sourceAccountId()).isEqualTo(source.getId());
                    assertThat(e.destinationAccountId()).isEqualTo(destination.getId());
                });
    }

    private static TransferCommand command(String amount) {
        return new TransferCommand("1000000101", "1000000102", new BigDecimal(amount), "VND", null);
    }

    private static Account account(String balance) {
        Customer customer = new Customer(UUID.randomUUID(), UUID.randomUUID(), "X", "x@x", null, Instant.now());
        return new Account(UUID.randomUUID(), customer, "1000000101", "VND", new BigDecimal(balance), Instant.now());
    }
}
