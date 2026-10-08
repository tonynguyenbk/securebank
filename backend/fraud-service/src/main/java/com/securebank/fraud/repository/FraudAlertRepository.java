package com.securebank.fraud.repository;

import com.securebank.fraud.domain.FraudAlert;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.UUID;

public interface FraudAlertRepository extends JpaRepository<FraudAlert, UUID>, JpaSpecificationExecutor<FraudAlert> {

    boolean existsByTransactionId(UUID transactionId);

    /** Dashboard counters in one round trip. HIGH / CRITICAL count only alerts still awaiting a decision. */
    @Query(value = """
            SELECT count(*) FILTER (WHERE status = 'OPEN')                                              AS open,
                   count(*) FILTER (WHERE status = 'UNDER_REVIEW')                                      AS underReview,
                   count(*) FILTER (WHERE risk_level = 'CRITICAL' AND status IN ('OPEN', 'UNDER_REVIEW')) AS critical,
                   count(*) FILTER (WHERE risk_level = 'HIGH' AND status IN ('OPEN', 'UNDER_REVIEW'))     AS high,
                   count(*) FILTER (WHERE created_at >= :startOfToday)                                  AS createdToday
            FROM fraud_alerts
            """, nativeQuery = true)
    AlertCounts countForDashboard(@Param("startOfToday") Instant startOfToday);

    interface AlertCounts {
        long getOpen();

        long getUnderReview();

        long getCritical();

        long getHigh();

        long getCreatedToday();
    }
}
