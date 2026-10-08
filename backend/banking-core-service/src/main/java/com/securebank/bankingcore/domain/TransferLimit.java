package com.securebank.bankingcore.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Outgoing transfer limits of one account (spec §14). */
@Entity
@Table(name = "transfer_limits")
public class TransferLimit extends BaseEntity {

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false, updatable = false)
    private Account account;

    @Column(name = "per_transaction_limit", nullable = false, precision = 19, scale = 2)
    private BigDecimal perTransactionLimit;

    @Column(name = "daily_limit", nullable = false, precision = 19, scale = 2)
    private BigDecimal dailyLimit;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "updated_by")
    private UUID updatedBy;

    protected TransferLimit() {
        // JPA
    }

    public TransferLimit(UUID id, Account account, BigDecimal perTransactionLimit, BigDecimal dailyLimit,
                         Instant now) {
        super(id);
        this.account = account;
        this.perTransactionLimit = Money.normalize(perTransactionLimit);
        this.dailyLimit = Money.normalize(dailyLimit);
        this.updatedAt = now;
    }

    public void update(BigDecimal perTransactionLimit, BigDecimal dailyLimit, UUID actor, Instant now) {
        if (perTransactionLimit.compareTo(dailyLimit) > 0) {
            throw new IllegalArgumentException("Per-transaction limit cannot exceed the daily limit");
        }
        this.perTransactionLimit = Money.normalize(perTransactionLimit);
        this.dailyLimit = Money.normalize(dailyLimit);
        this.updatedBy = actor;
        this.updatedAt = now;
    }

    public Account getAccount() {
        return account;
    }

    public BigDecimal getPerTransactionLimit() {
        return perTransactionLimit;
    }

    public BigDecimal getDailyLimit() {
        return dailyLimit;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public UUID getUpdatedBy() {
        return updatedBy;
    }
}
