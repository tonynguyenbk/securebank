package com.securebank.fraud.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;

import java.time.Instant;
import java.util.UUID;

/** One timeline entry of an alert: creation (OPEN, system) and every review step. */
@Entity
@Immutable
@Table(name = "fraud_alert_status_history")
public class FraudAlertStatusChange {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "alert_id", nullable = false)
    private UUID alertId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private FraudAlertStatus status;

    @Column(name = "actor_user_id")
    private UUID actorUserId;

    @Column(name = "actor_username")
    private String actorUsername;

    @Column
    private String note;

    @Column(name = "changed_at", nullable = false)
    private Instant changedAt;

    protected FraudAlertStatusChange() {
        // JPA
    }

    public FraudAlertStatusChange(UUID alertId, FraudAlertStatus status, UUID actorUserId, String actorUsername,
                                  String note, Instant changedAt) {
        this.alertId = alertId;
        this.status = status;
        this.actorUserId = actorUserId;
        this.actorUsername = actorUsername;
        this.note = note;
        this.changedAt = changedAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getAlertId() {
        return alertId;
    }

    public FraudAlertStatus getStatus() {
        return status;
    }

    public UUID getActorUserId() {
        return actorUserId;
    }

    public String getActorUsername() {
        return actorUsername;
    }

    public String getNote() {
        return note;
    }

    public Instant getChangedAt() {
        return changedAt;
    }
}
