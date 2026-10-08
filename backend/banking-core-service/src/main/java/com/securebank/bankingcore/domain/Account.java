package com.securebank.bankingcore.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * A current account. The balance only changes through {@link #debit} / {@link #credit}, which the transfer
 * engine calls while holding a pessimistic write lock on the row; each call returns the before/after
 * snapshot that goes into the ledger. {@code @Version} is kept as a second line of defence: any code path
 * that forgot to lock would fail with an optimistic-lock error instead of silently losing an update.
 */
@Entity
@Table(name = "accounts")
public class Account extends BaseEntity {

    public static final String TYPE_CURRENT = "CURRENT";

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false, updatable = false)
    private Customer customer;

    @Column(name = "account_number", nullable = false, updatable = false, length = 30)
    private String accountNumber;

    @Column(name = "account_type", nullable = false, updatable = false, length = 20)
    private String accountType;

    @Column(name = "currency", nullable = false, updatable = false, length = 3)
    private String currency;

    @Column(name = "balance", nullable = false, precision = 19, scale = 2)
    private BigDecimal balance;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private AccountStatus status;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Account() {
        // JPA
    }

    public Account(UUID id, Customer customer, String accountNumber, String currency, BigDecimal openingBalance,
                   Instant now) {
        super(id);
        this.customer = customer;
        this.accountNumber = accountNumber;
        this.accountType = TYPE_CURRENT;
        this.currency = currency;
        this.balance = Money.normalize(openingBalance);
        this.status = AccountStatus.ACTIVE;
        this.createdAt = now;
        this.updatedAt = now;
    }

    /** Removes money. Callers must have checked the balance; this is the last-resort invariant check. */
    public BalanceChange debit(BigDecimal amount, Instant now) {
        requirePositive(amount);
        BigDecimal before = balance;
        BigDecimal after = before.subtract(amount);
        if (after.signum() < 0) {
            throw new IllegalStateException("Debit would make the balance negative");
        }
        this.balance = after;
        this.updatedAt = now;
        return new BalanceChange(before, after);
    }

    public BalanceChange credit(BigDecimal amount, Instant now) {
        requirePositive(amount);
        BigDecimal before = balance;
        this.balance = before.add(amount);
        this.updatedAt = now;
        return new BalanceChange(before, balance);
    }

    public void changeStatus(AccountStatus newStatus, Instant now) {
        this.status = newStatus;
        this.updatedAt = now;
    }

    public boolean hasSufficientFunds(BigDecimal amount) {
        return balance.compareTo(amount) >= 0;
    }

    private static void requirePositive(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("Amount must be positive");
        }
    }

    public Customer getCustomer() {
        return customer;
    }

    public String getAccountNumber() {
        return accountNumber;
    }

    public String getAccountType() {
        return accountType;
    }

    public String getCurrency() {
        return currency;
    }

    public BigDecimal getBalance() {
        return balance;
    }

    public AccountStatus getStatus() {
        return status;
    }

    public long getVersion() {
        return version;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    /** Balance snapshot around one movement, recorded on the ledger line. */
    public record BalanceChange(BigDecimal before, BigDecimal after) {
    }
}
