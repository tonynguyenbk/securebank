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

import java.util.UUID;

/** A rule that contributed to an alert's score (immutable once written). */
@Entity
@Immutable
@Table(name = "fraud_rule_hits")
public class FraudAlertRuleHit {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "alert_id", nullable = false)
    private UUID alertId;

    @Enumerated(EnumType.STRING)
    @Column(name = "rule_code", nullable = false)
    private RuleCode ruleCode;

    @Column(nullable = false)
    private String description;

    @Column(name = "score_contribution", nullable = false)
    private int scoreContribution;

    @Column(nullable = false)
    private String details;

    protected FraudAlertRuleHit() {
        // JPA
    }

    public FraudAlertRuleHit(UUID alertId, RuleHit hit) {
        this.alertId = alertId;
        this.ruleCode = hit.ruleCode();
        this.description = hit.description();
        this.scoreContribution = hit.scoreContribution();
        this.details = hit.details();
    }

    public UUID getId() {
        return id;
    }

    public UUID getAlertId() {
        return alertId;
    }

    public RuleCode getRuleCode() {
        return ruleCode;
    }

    public String getDescription() {
        return description;
    }

    public int getScoreContribution() {
        return scoreContribution;
    }

    public String getDetails() {
        return details;
    }
}
