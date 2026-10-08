package com.securebank.audit.infrastructure;

import com.securebank.audit.application.AuditIngestService;
import com.securebank.common.events.AuditEvent;
import com.securebank.common.events.Topics;
import com.securebank.common.json.EventJson;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/** Consumes {@code bank.audit.event.v1} from every producing service. */
@Component
public class AuditEventListener {

    private final EventJson eventJson;
    private final AuditIngestService ingest;

    public AuditEventListener(EventJson eventJson, AuditIngestService ingest) {
        this.eventJson = eventJson;
        this.ingest = ingest;
    }

    @KafkaListener(topics = Topics.AUDIT_EVENT)
    public void onAuditEvent(String payload) {
        AuditEvent event;
        try {
            event = eventJson.read(payload, AuditEvent.class);
        } catch (IllegalArgumentException e) {
            throw new MalformedEventException("Unreadable AuditEvent", e);
        }
        if (event == null || event.eventId() == null || isBlank(event.action()) || isBlank(event.resourceType())
                || isBlank(event.sourceService())) {
            throw new MalformedEventException("AuditEvent misses required fields", null);
        }
        ingest.record(event);
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
