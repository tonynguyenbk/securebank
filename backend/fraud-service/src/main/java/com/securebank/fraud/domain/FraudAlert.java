package com.securebank.fraud.domain;

import com.securebank.common.error.ApiException;
import com.securebank.common.error.ErrorCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** A transaction that scored at or above the alert threshold, plus its review state. */
@Entity
@Table(name = "fraud_alerts")
public class FraudAlert {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "transaction_id", nullable = false, updatable = false)
    private UUID transactionId;

    @Column(name = "transaction_reference", nullable = false, updatable = false)
    private String transactionReference;

    @Column(name = "customer_id", nullable = false, updatable = false)
    private UUID customerId;

    @Column(name = "customer_name", nullable = false, updatable = false)
    private String customerName;

    @Column(name = "user_id", updatable = false)
    private UUID userId;

    @Column(name = "source_account_id", nullable = false, updatable = false)
    private UUID sourceAccountId;

    @Column(name = "source_account_number", nullable = false, updatable = false)
    private String sourceAccountNumber;

    @Column(name = "destination_account_number", nullable = false, updatable = false)
    private String destinationAccountNumber;

    @Column(name = "destination_customer_name", updatable = false)
    private String destinationCustomerName;

    @Column(nullable = false, updatable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, updatable = false)
    private String currency;

    @Column(name = "transaction_occurred_at", nullable = false, updatable = false)
    private Instant transactionOccurredAt;

    @Column(name = "risk_score", nullable = false, updatable = false)
    private int riskScore;

    @Enumerated(EnumType.STRING)
    @Column(name = "risk_level", nullable = false, updatable = false)
    private RiskLevel riskLevel;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private FraudAlertStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "reviewed_by")
    private UUID reviewedBy;

    @Column(name = "reviewed_by_username")
    private String reviewedByUsername;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @Column(name = "review_note")
    private String reviewNote;

    @Version
    private long version;

    protected FraudAlert() {
        // JPA
    }

    public FraudAlert(UUID transactionId, String transactionReference, UUID customerId, String customerName,
                      UUID userId, UUID sourceAccountId, String sourceAccountNumber, String destinationAccountNumber,
                      String destinationCustomerName, BigDecimal amount, String currency,
                      Instant transactionOccurredAt, RiskAssessment assessment, Instant createdAt) {
        this.transactionId = transactionId;
        this.transactionReference = transactionReference;
        this.customerId = customerId;
        this.customerName = customerName;
        this.userId = userId;
        this.sourceAccountId = sourceAccountId;
        this.sourceAccountNumber = sourceAccountNumber;
        this.destinationAccountNumber = destinationAccountNumber;
        this.destinationCustomerName = destinationCustomerName;
        this.amount = amount;
        this.currency = currency;
        this.transactionOccurredAt = transactionOccurredAt;
        this.riskScore = assessment.score();
        this.riskLevel = assessment.level();
        this.status = FraudAlertStatus.OPEN;
        this.createdAt = createdAt;
    }

    /**
     * Applies an analyst decision. Throws {@code FRAUD_ALERT_INVALID_TRANSITION} if the workflow forbids it.
     * Note presence is validated by the caller (it is an input-validation concern, 400).
     */
    public void review(FraudAlertStatus target, UUID reviewerId, String reviewerUsername, String note, Instant at) {
        if (!status.canTransitionTo(target)) {
            throw new ApiException(ErrorCode.FRAUD_ALERT_INVALID_TRANSITION,
                    "A fraud alert in status %s cannot move to %s.".formatted(status, target));
        }
        this.status = target;
        this.reviewedBy = reviewerId;
        this.reviewedByUsername = reviewerUsername;
        this.reviewedAt = at;
        if (note != null) {
            this.reviewNote = note;
        }
    }

    public UUID getId() {
        return id;
    }

    public UUID getTransactionId() {
        return transactionId;
    }

    public String getTransactionReference() {
        return transactionReference;
    }

    public UUID getCustomerId() {
        return customerId;
    }

    public String getCustomerName() {
        return customerName;
    }

    public UUID getUserId() {
        return userId;
    }

    public UUID getSourceAccountId() {
        return sourceAccountId;
    }

    public String getSourceAccountNumber() {
        return sourceAccountNumber;
    }

    public String getDestinationAccountNumber() {
        return destinationAccountNumber;
    }

    public String getDestinationCustomerName() {
        return destinationCustomerName;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getCurrency() {
        return currency;
    }

    public Instant getTransactionOccurredAt() {
        return transactionOccurredAt;
    }

    public int getRiskScore() {
        return riskScore;
    }

    public RiskLevel getRiskLevel() {
        return riskLevel;
    }

    public FraudAlertStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public UUID getReviewedBy() {
        return reviewedBy;
    }

    public String getReviewedByUsername() {
        return reviewedByUsername;
    }

    public Instant getReviewedAt() {
        return reviewedAt;
    }

    public String getReviewNote() {
        return reviewNote;
    }

    public long getVersion() {
        return version;
    }
}
