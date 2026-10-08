package com.securebank.fraud.domain;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Objects;

/**
 * Configurable rule thresholds (values from {@code securebank.fraud.*}).
 *
 * @param highAmountThreshold       HIGH_AMOUNT fires when amount &gt;= this
 * @param highFrequencyMaxTransfers HIGH_FREQUENCY fires when transfers in the window &gt; this
 * @param highFrequencyWindow       sliding window length for HIGH_FREQUENCY
 * @param dailyVelocityThreshold    DAILY_VELOCITY fires when today's outgoing total &gt; this
 * @param newBeneficiaryMinAmount   NEW_BENEFICIARY fires for a first-time destination when amount &gt;= this
 * @param alertMinScore             an alert is raised when the score is &gt;= this
 */
public record FraudRuleThresholds(BigDecimal highAmountThreshold, int highFrequencyMaxTransfers,
                                  Duration highFrequencyWindow, BigDecimal dailyVelocityThreshold,
                                  BigDecimal newBeneficiaryMinAmount, int alertMinScore) {

    public FraudRuleThresholds {
        Objects.requireNonNull(highAmountThreshold, "highAmountThreshold");
        Objects.requireNonNull(highFrequencyWindow, "highFrequencyWindow");
        Objects.requireNonNull(dailyVelocityThreshold, "dailyVelocityThreshold");
        Objects.requireNonNull(newBeneficiaryMinAmount, "newBeneficiaryMinAmount");
    }

    public static FraudRuleThresholds defaults() {
        return new FraudRuleThresholds(new BigDecimal("100000000"), 5, Duration.ofSeconds(60),
                new BigDecimal("200000000"), new BigDecimal("10000000"), 30);
    }
}
