package com.securebank.fraud.config;

import com.securebank.fraud.domain.FraudRuleThresholds;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.ZoneId;

/**
 * Fraud rule thresholds, configurable per environment ({@code securebank.fraud.*}). Missing values fall
 * back to the contract defaults (api.md §4).
 */
@ConfigurationProperties(prefix = "securebank.fraud")
public record FraudProperties(BigDecimal highAmountThreshold,
                              Integer highFrequencyMaxTransfers,
                              Duration highFrequencyWindow,
                              BigDecimal dailyVelocityThreshold,
                              BigDecimal newBeneficiaryMinAmount,
                              Integer alertMinScore,
                              ZoneId businessZone) {

    public FraudProperties {
        FraudRuleThresholds d = FraudRuleThresholds.defaults();
        highAmountThreshold = highAmountThreshold != null ? highAmountThreshold : d.highAmountThreshold();
        highFrequencyMaxTransfers = highFrequencyMaxTransfers != null ? highFrequencyMaxTransfers : d.highFrequencyMaxTransfers();
        highFrequencyWindow = highFrequencyWindow != null ? highFrequencyWindow : d.highFrequencyWindow();
        dailyVelocityThreshold = dailyVelocityThreshold != null ? dailyVelocityThreshold : d.dailyVelocityThreshold();
        newBeneficiaryMinAmount = newBeneficiaryMinAmount != null ? newBeneficiaryMinAmount : d.newBeneficiaryMinAmount();
        alertMinScore = alertMinScore != null ? alertMinScore : d.alertMinScore();
        businessZone = businessZone != null ? businessZone : ZoneId.of("Asia/Ho_Chi_Minh");
    }

    public FraudRuleThresholds thresholds() {
        return new FraudRuleThresholds(highAmountThreshold, highFrequencyMaxTransfers, highFrequencyWindow,
                dailyVelocityThreshold, newBeneficiaryMinAmount, alertMinScore);
    }
}
