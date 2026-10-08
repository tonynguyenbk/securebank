package com.securebank.fraud.domain;

import java.util.List;

/** Result of running every rule against a transaction. */
public record RiskAssessment(List<RuleHit> hits, int score, RiskLevel level) {

    public RiskAssessment {
        hits = List.copyOf(hits);
    }

    public List<String> ruleCodes() {
        return hits.stream().map(h -> h.ruleCode().name()).toList();
    }
}
