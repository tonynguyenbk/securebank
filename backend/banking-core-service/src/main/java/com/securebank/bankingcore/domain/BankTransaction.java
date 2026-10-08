package com.securebank.bankingcore.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * A transfer attempt that reached the business rules. Written once, in its final state (SUCCESS or
 * REJECTED), and never updated — hence {@link Immutable}: Hibernate never issues an UPDATE for it.
 */
@Entity
@Immutable
@Table(name = "bank_transactions")
public class BankTransaction extends BaseEntity {

    @Column(name = "transaction_reference", nullable = false, updatable = false, length = 50)
    private String transactionReference;

    @Enumerated(EnumType.STRING)
    @Column(name = "transaction_type", nullable = false, updatable = false, length = 30)
    private TransactionType type;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "source_account_id", nullable = false, updatable = false)
    private Account sourceAccount;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "destination_account_id", updatable = false)
    private Account destinationAccount;

    @Column(name = "destination_account_number", nullable = false, updatable = false, length = 30)
    private String destinationAccountNumber;

    @Column(name = "amount", nullable = false, updatable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(name = "currency", nullable = false, updatable = false, length = 3)
    private String currency;

    @Column(name = "description", updatable = false)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, updatable = false, length = 20)
    private TransactionStatus status;

    @Column(name = "failure_code", updatable = false, length = 50)
    private String failureCode;

    @Column(name = "failure_reason", updatable = false)
    private String failureReason;

    @Column(name = "created_by", nullable = false, updatable = false)
    private UUID createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "completed_at", updatable = false)
    private Instant completedAt;

    protected BankTransaction() {
        // JPA
    }

    private BankTransaction(UUID id, String reference, Account source, Account destination,
                            String destinationAccountNumber, BigDecimal amount, String currency, String description,
                            TransactionStatus status, String failureCode, String failureReason, UUID createdBy,
                            Instant createdAt, Instant completedAt) {
        super(id);
        this.transactionReference = Objects.requireNonNull(reference);
        this.type = TransactionType.INTERNAL_TRANSFER;
        this.sourceAccount = Objects.requireNonNull(source);
        this.destinationAccount = destination;
        this.destinationAccountNumber = Objects.requireNonNull(destinationAccountNumber);
        this.amount = Money.normalize(amount);
        this.currency = currency;
        this.description = description;
        this.status = status;
        this.failureCode = failureCode;
        this.failureReason = failureReason;
        this.createdBy = createdBy;
        this.createdAt = createdAt;
        this.completedAt = completedAt;
    }

    public static BankTransaction success(UUID id, String reference, Account source, Account destination,
                                          BigDecimal amount, String currency, String description, UUID createdBy,
                                          Instant now) {
        return new BankTransaction(id, reference, source, destination, destination.getAccountNumber(), amount,
                currency, description, TransactionStatus.SUCCESS, null, null, createdBy, now, now);
    }

    public static BankTransaction rejected(UUID id, String reference, Account source, Account destination,
                                           String destinationAccountNumber, BigDecimal amount, String currency,
                                           String description, String failureCode, String failureReason,
                                           UUID createdBy, Instant now) {
        return new BankTransaction(id, reference, source, destination, destinationAccountNumber, amount, currency,
                description, TransactionStatus.REJECTED, failureCode, failureReason, createdBy, now, null);
    }

    public String getTransactionReference() {
        return transactionReference;
    }

    public TransactionType getType() {
        return type;
    }

    public Account getSourceAccount() {
        return sourceAccount;
    }

    public Account getDestinationAccount() {
        return destinationAccount;
    }

    public String getDestinationAccountNumber() {
        return destinationAccountNumber;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getCurrency() {
        return currency;
    }

    public String getDescription() {
        return description;
    }

    public TransactionStatus getStatus() {
        return status;
    }

    public String getFailureCode() {
        return failureCode;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public UUID getCreatedBy() {
        return createdBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }
}
