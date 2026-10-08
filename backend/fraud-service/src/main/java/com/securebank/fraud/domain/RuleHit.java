package com.securebank.fraud.domain;

/** One triggered rule with a human-readable explanation of why it fired. */
public record RuleHit(RuleCode ruleCode, String description, int scoreContribution, String details) {

    public static RuleHit of(RuleCode code, String details) {
        return new RuleHit(code, code.description(), code.weight(), details);
    }
}
