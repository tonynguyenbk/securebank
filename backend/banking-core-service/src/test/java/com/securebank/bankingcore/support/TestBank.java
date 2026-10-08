package com.securebank.bankingcore.support;

import com.securebank.bankingcore.application.limits.TransferLimitService;
import com.securebank.bankingcore.domain.Account;
import com.securebank.bankingcore.domain.AccountStatus;
import com.securebank.bankingcore.domain.Customer;
import com.securebank.bankingcore.domain.Money;
import com.securebank.bankingcore.repository.AccountRepository;
import com.securebank.bankingcore.repository.CustomerRepository;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Creates isolated test customers/accounts (fresh UUIDs and sequence numbers) and reads balances back. */
public class TestBank {

    private final TransactionTemplate tx;
    private final CustomerRepository customers;
    private final AccountRepository accounts;
    private final TransferLimitService limits;

    public TestBank(TransactionTemplate tx, CustomerRepository customers, AccountRepository accounts,
                    TransferLimitService limits) {
        this.tx = tx;
        this.customers = customers;
        this.accounts = accounts;
        this.limits = limits;
    }

    public TestAccount open(String fullName, String balance) {
        return tx.execute(status -> {
            Instant now = Instant.now();
            UUID userId = UUID.randomUUID();
            Customer customer = customers.save(new Customer(UUID.randomUUID(), userId, fullName,
                    userId + "@test.local", null, now));
            String number = String.valueOf(accounts.nextAccountNumber());
            Account account = accounts.save(new Account(UUID.randomUUID(), customer, number, Money.VND,
                    new BigDecimal(balance), now));
            limits.openDefault(account);
            return new TestAccount(userId, customer.getId(), account.getId(), number);
        });
    }

    public BigDecimal balance(TestAccount account) {
        return tx.execute(status -> accounts.findById(account.accountId()).orElseThrow().getBalance());
    }

    public AccountStatus status(TestAccount account) {
        return tx.execute(status -> accounts.findById(account.accountId()).orElseThrow().getStatus());
    }

    public void setStatus(TestAccount account, AccountStatus newStatus) {
        tx.executeWithoutResult(status -> accounts.findById(account.accountId()).orElseThrow()
                .changeStatus(newStatus, Instant.now()));
    }

    public record TestAccount(UUID userId, UUID customerId, UUID accountId, String accountNumber) {
    }
}
