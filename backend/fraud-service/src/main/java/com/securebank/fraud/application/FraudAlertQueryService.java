package com.securebank.fraud.application;

import com.securebank.common.error.ApiException;
import com.securebank.common.error.ErrorCode;
import com.securebank.common.web.PageResponse;
import com.securebank.fraud.application.dto.FraudAlertDetail;
import com.securebank.fraud.application.dto.FraudAlertFilter;
import com.securebank.fraud.application.dto.FraudAlertStats;
import com.securebank.fraud.application.dto.FraudAlertSummary;
import com.securebank.fraud.config.FraudProperties;
import com.securebank.fraud.domain.FraudAlert;
import com.securebank.fraud.repository.FraudAlertRepository;
import com.securebank.fraud.repository.FraudAlertRuleHitRepository;
import com.securebank.fraud.repository.FraudAlertStatusChangeRepository;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class FraudAlertQueryService {

    private final FraudAlertRepository alerts;
    private final FraudAlertRuleHitRepository ruleHits;
    private final FraudAlertStatusChangeRepository history;
    private final FraudProperties properties;
    private final Clock clock;

    public FraudAlertQueryService(FraudAlertRepository alerts, FraudAlertRuleHitRepository ruleHits,
                                  FraudAlertStatusChangeRepository history, FraudProperties properties, Clock clock) {
        this.alerts = alerts;
        this.ruleHits = ruleHits;
        this.history = history;
        this.properties = properties;
        this.clock = clock;
    }

    public PageResponse<FraudAlertSummary> list(FraudAlertFilter filter, Pageable pageable) {
        return PageResponse.of(alerts.findAll(specification(filter), pageable), FraudAlertSummary::from);
    }

    public FraudAlertDetail get(UUID id) {
        FraudAlert alert = alerts.findById(id).orElseThrow(() -> new ApiException(ErrorCode.FRAUD_ALERT_NOT_FOUND));
        return toDetail(alert);
    }

    /** Three indexed lookups (alert, its rules, its timeline) — no lazy collections, no N+1. */
    FraudAlertDetail toDetail(FraudAlert alert) {
        return FraudAlertDetail.from(alert,
                ruleHits.findByAlertIdOrderByScoreContributionDescRuleCodeAsc(alert.getId()),
                history.findByAlertIdOrderByChangedAtAsc(alert.getId()));
    }

    public FraudAlertStats stats() {
        Instant startOfToday = LocalDate.now(clock.withZone(properties.businessZone()))
                .atStartOfDay(properties.businessZone()).toInstant();
        FraudAlertRepository.AlertCounts c = alerts.countForDashboard(startOfToday);
        return new FraudAlertStats(c.getOpen(), c.getUnderReview(), c.getCritical(), c.getHigh(), c.getCreatedToday());
    }

    private static Specification<FraudAlert> specification(FraudAlertFilter filter) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>(3);
            if (filter.status() != null) {
                predicates.add(cb.equal(root.get("status"), filter.status()));
            }
            if (filter.riskLevel() != null) {
                predicates.add(cb.equal(root.get("riskLevel"), filter.riskLevel()));
            }
            if (filter.customerId() != null) {
                predicates.add(cb.equal(root.get("customerId"), filter.customerId()));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }
}
