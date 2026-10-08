package com.securebank.audit.application;

import com.securebank.audit.repository.AuditLogWriter;
import com.securebank.common.events.AuditEvent;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Clock;

/** Stores consumed {@link AuditEvent}s. Duplicate deliveries are ignored by the unique event_id. */
@Service
public class AuditIngestService {

    private static final Logger log = LoggerFactory.getLogger(AuditIngestService.class);

    private final AuditLogWriter writer;
    private final MeterRegistry meters;
    private final Clock clock;

    public AuditIngestService(AuditLogWriter writer, MeterRegistry meters, Clock clock) {
        this.writer = writer;
        this.meters = meters;
        this.clock = clock;
    }

    public boolean record(AuditEvent event) {
        String outcome = AuditEvent.OUTCOME_FAILURE.equals(event.outcome())
                ? AuditEvent.OUTCOME_FAILURE : AuditEvent.OUTCOME_SUCCESS;
        boolean inserted = writer.insertIfAbsent(event, outcome, clock.instant());
        if (inserted) {
            meters.counter("audit_log_recorded_total", "action", event.action()).increment();
            log.info("Audit recorded eventId={} action={} resource={}:{} source={}", event.eventId(), event.action(),
                    event.resourceType(), event.resourceId(), event.sourceService());
        } else {
            log.info("Duplicate audit eventId={} ignored", event.eventId());
        }
        return inserted;
    }
}
