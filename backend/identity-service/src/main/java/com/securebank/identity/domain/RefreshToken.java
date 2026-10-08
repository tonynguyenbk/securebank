package com.securebank.identity.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import org.springframework.data.domain.Persistable;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Server-side record of an opaque refresh token. Only the SHA-256 hash is stored, so a database
 * leak does not reveal usable tokens.
 */
@Entity
@Table(name = "refresh_tokens")
public class RefreshToken implements Persistable<UUID> {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "token_hash", nullable = false, unique = true, length = 64, updatable = false)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private Instant expiresAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "replaced_by_id")
    private UUID replacedById;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "created_ip", length = 64, updatable = false)
    private String createdIp;

    @Column(name = "user_agent", length = 255, updatable = false)
    private String userAgent;

    @Transient
    private boolean isNew = true;

    protected RefreshToken() {
    }

    public RefreshToken(UUID id, UUID userId, String tokenHash, Instant createdAt, Instant expiresAt,
                        String createdIp, String userAgent) {
        this.id = Objects.requireNonNull(id);
        this.userId = Objects.requireNonNull(userId);
        this.tokenHash = Objects.requireNonNull(tokenHash);
        this.createdAt = Objects.requireNonNull(createdAt);
        this.expiresAt = Objects.requireNonNull(expiresAt);
        this.createdIp = createdIp;
        this.userAgent = userAgent;
    }

    @PostLoad
    @PostPersist
    void markNotNew() {
        this.isNew = false;
    }

    @Override
    public boolean isNew() {
        return isNew;
    }

    public boolean isRevoked() {
        return revokedAt != null;
    }

    public boolean isExpired(Instant now) {
        return !expiresAt.isAfter(now);
    }

    public void revoke(Instant now) {
        if (revokedAt == null) {
            revokedAt = now;
        }
    }

    /** Rotation: this token is consumed and replaced by {@code successorId}. */
    public void rotateTo(UUID successorId, Instant now) {
        revoke(now);
        this.replacedById = successorId;
    }

    @Override
    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getTokenHash() {
        return tokenHash;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getRevokedAt() {
        return revokedAt;
    }

    public UUID getReplacedById() {
        return replacedById;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
