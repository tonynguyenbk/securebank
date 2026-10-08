package com.securebank.fraud.repository;

import com.securebank.fraud.domain.FraudAlertStatusChange;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface FraudAlertStatusChangeRepository extends JpaRepository<FraudAlertStatusChange, UUID> {

    List<FraudAlertStatusChange> findByAlertIdOrderByChangedAtAsc(UUID alertId);
}
