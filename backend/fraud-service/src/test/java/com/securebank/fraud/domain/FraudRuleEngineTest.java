package com.securebank.fraud.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class FraudRuleEngineTest {

    private final FraudRuleEngine engine = new FraudRuleEngine(FraudRuleThresholds.defaults());

    private static BigDecimal vnd(long amount) {
        return BigDecimal.valueOf(amount).setScale(2);
    }

    private static TransactionSignals quiet(BigDecimal todayTotal) {
        return new TransactionSignals(1, todayTotal, false);
    }

    @Test
    void smallKnownTransferTriggersNothing() {
        RiskAssessment a = engine.evaluate(vnd(1_000_000), quiet(vnd(1_000_000)));
        assertThat(a.hits()).isEmpty();
        assertThat(a.score()).isZero();
        assertThat(a.level()).isEqualTo(RiskLevel.LOW);
        assertThat(engine.requiresAlert(a)).isFalse();
    }

    @Test
    void highAmountAtThresholdScores40() {
        RiskAssessment a = engine.evaluate(vnd(100_000_000), quiet(vnd(100_000_000)));
        assertThat(a.ruleCodes()).containsExactly("HIGH_AMOUNT");
        assertThat(a.score()).isEqualTo(40);
        assertThat(a.level()).isEqualTo(RiskLevel.MEDIUM);
        assertThat(engine.requiresAlert(a)).isTrue();
    }

    @Test
    void highAmountJustBelowThresholdDoesNotFire() {
        RiskAssessment a = engine.evaluate(new BigDecimal("99999999.99"), quiet(vnd(99_999_999)));
        assertThat(a.hits()).isEmpty();
    }

    @Test
    void highFrequencyFiresOnlyAboveFiveTransfers() {
        assertThat(engine.evaluate(vnd(1_000_000), new TransactionSignals(5, vnd(5_000_000), false)).hits()).isEmpty();
        RiskAssessment a = engine.evaluate(vnd(1_000_000), new TransactionSignals(6, vnd(6_000_000), false));
        assertThat(a.ruleCodes()).containsExactly("HIGH_FREQUENCY");
        assertThat(a.score()).isEqualTo(30);
        assertThat(a.level()).isEqualTo(RiskLevel.MEDIUM);
        assertThat(a.hits().getFirst().details()).contains("6 outgoing transfers within 60 s");
    }

    @Test
    void dailyVelocityFiresOnlyAboveThreshold() {
        assertThat(engine.evaluate(vnd(1_000_000), quiet(vnd(200_000_000))).hits()).isEmpty();
        RiskAssessment a = engine.evaluate(vnd(1_000_000), quiet(new BigDecimal("200000000.01")));
        assertThat(a.ruleCodes()).containsExactly("DAILY_VELOCITY");
        assertThat(a.score()).isEqualTo(20);
        assertThat(a.level()).isEqualTo(RiskLevel.LOW);
        assertThat(engine.requiresAlert(a)).isFalse();
    }

    @Test
    void newBeneficiaryNeedsFirstTransferAndSignificantAmount() {
        assertThat(engine.evaluate(new BigDecimal("9999999.99"), new TransactionSignals(1, vnd(9_999_999), true))
                .hits()).isEmpty();
        assertThat(engine.evaluate(vnd(10_000_000), new TransactionSignals(1, vnd(10_000_000), false)).hits()).isEmpty();
        RiskAssessment a = engine.evaluate(vnd(10_000_000), new TransactionSignals(1, vnd(10_000_000), true));
        assertThat(a.ruleCodes()).containsExactly("NEW_BENEFICIARY");
        assertThat(a.score()).isEqualTo(20);
    }

    @Test
    void highAmountToNewBeneficiaryIsHigh() {
        RiskAssessment a = engine.evaluate(vnd(100_000_000), new TransactionSignals(1, vnd(100_000_000), true));
        assertThat(a.ruleCodes()).containsExactly("HIGH_AMOUNT", "NEW_BENEFICIARY");
        assertThat(a.score()).isEqualTo(60);
        assertThat(a.level()).isEqualTo(RiskLevel.HIGH);
    }

    @Test
    void allRulesTogetherAreCritical() {
        RiskAssessment a = engine.evaluate(vnd(150_000_000), new TransactionSignals(7, vnd(250_000_000), true));
        assertThat(a.ruleCodes()).containsExactly("HIGH_AMOUNT", "HIGH_FREQUENCY", "DAILY_VELOCITY", "NEW_BENEFICIARY");
        assertThat(a.score()).isEqualTo(110);
        assertThat(a.level()).isEqualTo(RiskLevel.CRITICAL);
        assertThat(a.hits()).extracting(RuleHit::scoreContribution).containsExactly(40, 30, 20, 20);
    }

    @Test
    void frequencyPlusVelocityIsMedium() {
        RiskAssessment a = engine.evaluate(vnd(5_000_000), new TransactionSignals(6, vnd(201_000_000), false));
        assertThat(a.score()).isEqualTo(50);
        assertThat(a.level()).isEqualTo(RiskLevel.MEDIUM);
    }

    @Test
    void thresholdsAreConfigurable() {
        var custom = new FraudRuleEngine(new FraudRuleThresholds(vnd(1_000), 2, Duration.ofSeconds(10), vnd(5_000),
                vnd(500), 30));
        RiskAssessment a = custom.evaluate(vnd(1_000), new TransactionSignals(3, vnd(6_000), true));
        assertThat(a.score()).isEqualTo(110);
    }

    @ParameterizedTest
    @CsvSource({"0,LOW", "29,LOW", "30,MEDIUM", "59,MEDIUM", "60,HIGH", "79,HIGH", "80,CRITICAL", "110,CRITICAL"})
    void levelBoundaries(int score, RiskLevel expected) {
        assertThat(RiskLevel.fromScore(score)).isEqualTo(expected);
    }
}
