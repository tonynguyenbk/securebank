package com.securebank.fraud.application;

import com.securebank.common.events.AuditEvent;
import com.securebank.common.events.Events;
import com.securebank.common.events.FraudAlertCreatedEvent;
import com.securebank.common.events.Topics;
import com.securebank.common.events.TransactionCompletedEvent;
import com.securebank.common.kafka.ProcessedEventStore;
import com.securebank.common.outbox.OutboxWriter;
import com.securebank.common.web.CorrelationId;
import com.securebank.common.web.Masking;
import com.securebank.fraud.domain.FraudAlert;
import com.securebank.fraud.domain.FraudAlertRuleHit;
import com.securebank.fraud.domain.FraudAlertStatus;
import com.securebank.fraud.domain.FraudAlertStatusChange;
import com.securebank.fraud.domain.FraudRuleEngine;
import com.securebank.fraud.domain.RiskAssessment;
import com.securebank.fraud.domain.TransactionSignals;
import com.securebank.fraud.repository.FraudAlertRepository;
import com.securebank.fraud.repository.FraudAlertRuleHitRepository;
import com.securebank.fraud.repository.FraudAlertStatusChangeRepository;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Scores every completed transaction and raises an alert (plus outbox events) when the score is MEDIUM or higher. */
@Service
public class FraudDetectionService {

    public static final String CONSUMER_NAME = "fraud-rule-engine";
    static final String AGGREGATE_TYPE = "FRAUD_ALERT";
    static final String SOURCE_SERVICE = "fraud-service";

    private static final Logger log = LoggerFactory.getLogger(FraudDetectionService.class);

    private final ProcessedEventStore processedEvents;
    private final FraudSignals signals;
    private final FraudRuleEngine engine;
    private final FraudAlertRepository alerts;
    private final FraudAlertRuleHitRepository ruleHits;
    private final FraudAlertStatusChangeRepository history;
    private final OutboxWriter outbox;
    private final MeterRegistry meters;
    private final Clock clock;

    public FraudDetectionService(ProcessedEventStore processedEvents, FraudSignals signals, FraudRuleEngine engine,
                                 FraudAlertRepository alerts, FraudAlertRuleHitRepository ruleHits,
                                 FraudAlertStatusChangeRepository history, OutboxWriter outbox,
                                 MeterRegistry meters, Clock clock) {
        this.processedEvents = processedEvents;
        this.signals = signals;
        this.engine = engine;
        this.alerts = alerts;
        this.ruleHits = ruleHits;
        this.history = history;
        this.outbox = outbox;
        this.meters = meters;
        this.clock = clock;
    }

    /**
     * Idempotent: the event is claimed in {@code processed_events} before any Redis side effect, so a
     * redelivered event is skipped and never double-counts the velocity/frequency signals.
     *
     * @return the created alert id, if any
     */
    @Transactional
    public Optional<UUID> process(TransactionCompletedEvent event) {
        if (!processedEvents.markIfFirst(event.eventId(), CONSUMER_NAME)) {
            log.info("Duplicate TransactionCompleted eventId={} ignored", event.eventId());
            return Optional.empty();
        }
        if (alerts.existsByTransactionId(event.transactionId())) {
            log.info("Transaction {} already has a fraud alert; event {} ignored",
                    event.transactionReference(), event.eventId());
            return Optional.empty();
        }

        Instant occurredAt = event.occurredAt() != null ? event.occurredAt() : clock.instant();
        TransactionSignals collected = signals.recordAndCollect(new FraudSignals.OutgoingTransfer(
                event.customerId(), event.transactionId(), event.destinationAccountId(), event.amount(), occurredAt));
        RiskAssessment assessment = engine.evaluate(event.amount(), collected);

        log.info("Fraud evaluation tx={} account={} score={} level={} rules={}", event.transactionReference(),
                Masking.accountNumber(event.sourceAccountNumber()), assessment.score(), assessment.level(),
                assessment.ruleCodes());
        if (!engine.requiresAlert(assessment)) {
            return Optional.empty();
        }
        return Optional.of(createAlert(event, occurredAt, assessment));
    }

    private UUID createAlert(TransactionCompletedEvent event, Instant occurredAt, RiskAssessment assessment) {
        Instant now = clock.instant();
        FraudAlert alert = alerts.save(new FraudAlert(event.transactionId(), event.transactionReference(),
                event.customerId(), event.customerName(), event.sourceUserId(), event.sourceAccountId(),
                event.sourceAccountNumber(), event.destinationAccountNumber(), event.destinationCustomerName(),
                event.amount(), event.currency(), occurredAt, assessment, now));
        UUID alertId = alert.getId();
        ruleHits.saveAll(assessment.hits().stream().map(h -> new FraudAlertRuleHit(alertId, h)).toList());
        history.save(new FraudAlertStatusChange(alertId, FraudAlertStatus.OPEN, null, null,
                "Alert raised by rule engine (score %d)".formatted(assessment.score()), now));

        outbox.append(AGGREGATE_TYPE, alertId, Topics.FRAUD_ALERT_CREATED, event.customerId().toString(),
                new FraudAlertCreatedEvent(Events.newId(), FraudAlertCreatedEvent.TYPE, Events.VERSION_1, now,
                        alertId, event.transactionId(), event.transactionReference(), event.customerId(),
                        event.sourceUserId(), event.sourceAccountId(), event.amount(), event.currency(),
                        assessment.score(), assessment.level().name(), assessment.ruleCodes()));

        Map<String, Object> after = new LinkedHashMap<>();
        after.put("status", FraudAlertStatus.OPEN.name());
        after.put("riskScore", assessment.score());
        after.put("riskLevel", assessment.level().name());
        after.put("rules", assessment.ruleCodes());
        after.put("transactionId", event.transactionId().toString());
        after.put("transactionReference", event.transactionReference());
        outbox.append(AGGREGATE_TYPE, alertId, Topics.AUDIT_EVENT, alertId.toString(),
                new AuditEvent(Events.newId(), AuditEvent.TYPE, Events.VERSION_1, now,
                        null, null, null, "FRAUD_ALERT_CREATED", "FRAUD_ALERT", alertId.toString(),
                        null, after, CorrelationId.current(), null, SOURCE_SERVICE, AuditEvent.OUTCOME_SUCCESS));

        meters.counter("fraud_alert_total", "riskLevel", assessment.level().name()).increment();
        log.warn("Fraud alert {} created for tx={} level={} score={}", alertId, event.transactionReference(),
                assessment.level(), assessment.score());
        return alertId;
    }
}
