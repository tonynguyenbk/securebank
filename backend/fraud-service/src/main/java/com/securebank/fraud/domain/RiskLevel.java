package com.securebank.fraud.domain;

/** Score bands: 0–29 LOW, 30–59 MEDIUM, 60–79 HIGH, 80+ CRITICAL. */
public enum RiskLevel {
    LOW,
    MEDIUM,
    HIGH,
    CRITICAL;

    public static RiskLevel fromScore(int score) {
        if (score >= 80) {
            return CRITICAL;
        }
        if (score >= 60) {
            return HIGH;
        }
        if (score >= 30) {
            return MEDIUM;
        }
        return LOW;
    }
}
