package com.securebank.fraud.domain;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Pure rule evaluation: no Spring, no Redis, no clock. Given the transaction amount and the precomputed
 * behavioural signals it returns the triggered rules, the total score and the risk level. Thresholds are
 * injected so they stay configurable; weights are fixed by the contract ({@link RuleCode}).
 */
public class FraudRuleEngine {

    private final FraudRuleThresholds thresholds;

    public FraudRuleEngine(FraudRuleThresholds thresholds) {
        this.thresholds = Objects.requireNonNull(thresholds);
    }

    public RiskAssessment evaluate(BigDecimal amount, TransactionSignals signals) {
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(signals, "signals");
        List<RuleHit> hits = new ArrayList<>(4);

        if (amount.compareTo(thresholds.highAmountThreshold()) >= 0) {
            hits.add(RuleHit.of(RuleCode.HIGH_AMOUNT,
                    "Amount %s VND >= threshold %s VND".formatted(plain(amount), plain(thresholds.highAmountThreshold()))));
        }
        if (signals.outgoingTransfersInWindow() > thresholds.highFrequencyMaxTransfers()) {
            hits.add(RuleHit.of(RuleCode.HIGH_FREQUENCY,
                    "%d outgoing transfers within %d s (allowed: %d)".formatted(signals.outgoingTransfersInWindow(),
                            thresholds.highFrequencyWindow().toSeconds(), thresholds.highFrequencyMaxTransfers())));
        }
        BigDecimal today = signals.outgoingTotalToday() == null ? BigDecimal.ZERO : signals.outgoingTotalToday();
        if (today.compareTo(thresholds.dailyVelocityThreshold()) > 0) {
            hits.add(RuleHit.of(RuleCode.DAILY_VELOCITY,
                    "Outgoing total today %s VND > threshold %s VND".formatted(plain(today),
                            plain(thresholds.dailyVelocityThreshold()))));
        }
        if (signals.firstTransferToBeneficiary() && amount.compareTo(thresholds.newBeneficiaryMinAmount()) >= 0) {
            hits.add(RuleHit.of(RuleCode.NEW_BENEFICIARY,
                    "First transfer to this destination account; amount %s VND >= %s VND".formatted(plain(amount),
                            plain(thresholds.newBeneficiaryMinAmount()))));
        }

        int score = hits.stream().mapToInt(RuleHit::scoreContribution).sum();
        return new RiskAssessment(hits, score, RiskLevel.fromScore(score));
    }

    public boolean requiresAlert(RiskAssessment assessment) {
        return assessment.score() >= thresholds.alertMinScore();
    }

    private static String plain(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
    }
}
