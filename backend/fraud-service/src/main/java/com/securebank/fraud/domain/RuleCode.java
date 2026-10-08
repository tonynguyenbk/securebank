package com.securebank.fraud.domain;

/** Fraud rules and their fixed score contributions (api.md §4, spec §15). */
public enum RuleCode {
    HIGH_AMOUNT(40, "Single transfer amount at or above the high-amount threshold"),
    HIGH_FREQUENCY(30, "Too many outgoing transfers from the customer in a short window"),
    DAILY_VELOCITY(20, "Customer's outgoing total today exceeds the daily velocity threshold"),
    NEW_BENEFICIARY(20, "First transfer from the customer to this destination account with a significant amount");

    private final int weight;
    private final String description;

    RuleCode(int weight, String description) {
        this.weight = weight;
        this.description = description;
    }

    public int weight() {
        return weight;
    }

    public String description() {
        return description;
    }
}
