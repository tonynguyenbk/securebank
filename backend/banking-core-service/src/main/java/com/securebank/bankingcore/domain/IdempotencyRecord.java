package com.securebank.bankingcore.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * Stored outcome of a POST /transfers keyed by (userId, idempotencyKey). The row is inserted ("claimed") at
 * the start of the transfer transaction and completed with the HTTP response before commit, so other
 * transactions only ever see completed rows.
 */
@Entity
@Table(name = "idempotency_records")
public class IdempotencyRecord extends BaseEntity {

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "idempotency_key", nullable = false, updatable = false, length = 100)
    private String idempotencyKey;

    @Column(name = "request_hash", nullable = false, updatable = false, length = 128)
    private String requestHash;

    @Column(name = "transaction_id")
    private UUID transactionId;

    @Column(name = "response_code")
    private Integer responseCode;

    @Column(name = "response_body", columnDefinition = "text")
    private String responseBody;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    protected IdempotencyRecord() {
        // JPA
    }

    public void complete(UUID transactionId, int responseCode, String responseBody, Instant now) {
        this.transactionId = transactionId;
        this.responseCode = responseCode;
        this.responseBody = responseBody;
        this.completedAt = now;
    }

    public boolean matches(String hash) {
        return requestHash.equals(hash);
    }

    public boolean isCompleted() {
        return responseCode != null && responseBody != null;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public String getRequestHash() {
        return requestHash;
    }

    public UUID getTransactionId() {
        return transactionId;
    }

    public Integer getResponseCode() {
        return responseCode;
    }

    public String getResponseBody() {
        return responseBody;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }
}
