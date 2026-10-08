package com.securebank.fraud.repository;

import com.securebank.fraud.domain.FraudAlertRuleHit;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface FraudAlertRuleHitRepository extends JpaRepository<FraudAlertRuleHit, UUID> {

    List<FraudAlertRuleHit> findByAlertIdOrderByScoreContributionDescRuleCodeAsc(UUID alertId);
}
