package com.securebank.fraud.application.dto;

import com.securebank.fraud.domain.FraudAlert;
import com.securebank.fraud.domain.FraudAlertRuleHit;
import com.securebank.fraud.domain.FraudAlertStatus;
import com.securebank.fraud.domain.FraudAlertStatusChange;
import com.securebank.fraud.domain.RiskLevel;
import com.securebank.fraud.domain.RuleCode;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** {@code FraudAlertDetail} of api.md §4 (summary fields flattened, plus rules and timeline). */
public record FraudAlertDetail(
        UUID id,
        UUID transactionId,
        String transactionReference,
        UUID customerId,
        String customerName,
        UUID sourceAccountId,
        String sourceAccountNumber,
        BigDecimal amount,
        String currency,
        int riskScore,
        RiskLevel riskLevel,
        FraudAlertStatus status,
        Instant createdAt,
        String destinationAccountNumber,
        String destinationCustomerName,
        Instant transactionOccurredAt,
        List<Rule> rules,
        UUID reviewedBy,
        String reviewedByUsername,
        Instant reviewedAt,
        String reviewNote,
        List<TimelineEntry> timeline
) {
    public record Rule(RuleCode ruleCode, String description, int scoreContribution, String details) {
    }

    public record TimelineEntry(Instant at, FraudAlertStatus status, String actorUsername, String note) {
    }

    public static FraudAlertDetail from(FraudAlert a, List<FraudAlertRuleHit> hits, List<FraudAlertStatusChange> changes) {
        return new FraudAlertDetail(a.getId(), a.getTransactionId(), a.getTransactionReference(), a.getCustomerId(),
                a.getCustomerName(), a.getSourceAccountId(), a.getSourceAccountNumber(), a.getAmount(),
                a.getCurrency(), a.getRiskScore(), a.getRiskLevel(), a.getStatus(), a.getCreatedAt(),
                a.getDestinationAccountNumber(), a.getDestinationCustomerName(), a.getTransactionOccurredAt(),
                hits.stream().map(h -> new Rule(h.getRuleCode(), h.getDescription(), h.getScoreContribution(),
                        h.getDetails())).toList(),
                a.getReviewedBy(), a.getReviewedByUsername(), a.getReviewedAt(), a.getReviewNote(),
                changes.stream().map(c -> new TimelineEntry(c.getChangedAt(), c.getStatus(), c.getActorUsername(),
                        c.getNote())).toList());
    }
}
