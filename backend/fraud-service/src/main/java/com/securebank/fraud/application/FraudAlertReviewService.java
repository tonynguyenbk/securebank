package com.securebank.fraud.application;

import com.securebank.common.error.ApiException;
import com.securebank.common.error.ErrorCode;
import com.securebank.common.events.AuditEvent;
import com.securebank.common.events.Events;
import com.securebank.common.events.Topics;
import com.securebank.common.outbox.OutboxWriter;
import com.securebank.common.security.AuthenticatedUser;
import com.securebank.common.web.CorrelationId;
import com.securebank.fraud.application.dto.FraudAlertDetail;
import com.securebank.fraud.application.dto.ReviewFraudAlertRequest;
import com.securebank.fraud.domain.FraudAlert;
import com.securebank.fraud.domain.FraudAlertStatus;
import com.securebank.fraud.domain.FraudAlertStatusChange;
import com.securebank.fraud.domain.ReviewPolicy;
import com.securebank.fraud.repository.FraudAlertRepository;
import com.securebank.fraud.repository.FraudAlertStatusChangeRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Analyst decisions on alerts. Optimistic locking (@Version) rejects concurrent reviews of the same alert. */
@Service
public class FraudAlertReviewService {

    private static final Logger log = LoggerFactory.getLogger(FraudAlertReviewService.class);

    private final FraudAlertRepository alerts;
    private final FraudAlertStatusChangeRepository history;
    private final FraudAlertQueryService queries;
    private final OutboxWriter outbox;
    private final Clock clock;

    public FraudAlertReviewService(FraudAlertRepository alerts, FraudAlertStatusChangeRepository history,
                                   FraudAlertQueryService queries, OutboxWriter outbox, Clock clock) {
        this.alerts = alerts;
        this.history = history;
        this.queries = queries;
        this.outbox = outbox;
        this.clock = clock;
    }

    @Transactional
    public FraudAlertDetail review(UUID alertId, ReviewFraudAlertRequest request, AuthenticatedUser actor,
                                   String ipAddress) {
        FraudAlert alert = alerts.findById(alertId)
                .orElseThrow(() -> new ApiException(ErrorCode.FRAUD_ALERT_NOT_FOUND));
        FraudAlertStatus before = alert.getStatus();
        String note = ReviewPolicy.validate(before, request.status(), request.note());

        Instant now = clock.instant();
        alert.review(request.status(), actor.userId(), actor.username(), note, now);
        // flush now so a concurrent review fails here (version check) before anything else is written
        alerts.saveAndFlush(alert);
        history.save(new FraudAlertStatusChange(alert.getId(), request.status(), actor.userId(), actor.username(),
                note, now));

        Map<String, Object> beforeState = Map.of("status", before.name());
        Map<String, Object> afterState = new LinkedHashMap<>();
        afterState.put("status", request.status().name());
        if (note != null) {
            afterState.put("note", note);
        }
        outbox.append(FraudDetectionService.AGGREGATE_TYPE, alert.getId(), Topics.AUDIT_EVENT,
                alert.getId().toString(),
                new AuditEvent(Events.newId(), AuditEvent.TYPE, Events.VERSION_1, now,
                        actor.userId(), actor.username(), actor.primaryRole().name(),
                        "FRAUD_ALERT_REVIEW", "FRAUD_ALERT", alert.getId().toString(),
                        beforeState, afterState, CorrelationId.current(), ipAddress,
                        FraudDetectionService.SOURCE_SERVICE, AuditEvent.OUTCOME_SUCCESS));

        log.info("Fraud alert {} reviewed by {}: {} -> {}", alert.getId(), actor.username(), before, request.status());
        return queries.toDetail(alert);
    }
}
