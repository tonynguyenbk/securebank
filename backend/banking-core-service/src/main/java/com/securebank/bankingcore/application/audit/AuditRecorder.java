package com.securebank.bankingcore.application.audit;

import com.securebank.bankingcore.infrastructure.web.ClientIpResolver;
import com.securebank.common.events.AuditEvent;
import com.securebank.common.events.Events;
import com.securebank.common.events.Topics;
import com.securebank.common.outbox.OutboxWriter;
import com.securebank.common.web.CorrelationId;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.Map;
import java.util.UUID;

/**
 * Writes {@link AuditEvent}s to the transactional outbox, i.e. in the same database transaction as the
 * change being audited: an audited change can never commit without its audit record, and vice versa.
 */
@Component
public class AuditRecorder {

    private final OutboxWriter outbox;
    private final ClientIpResolver ipResolver;
    private final Clock clock;
    private final String serviceName;

    public AuditRecorder(OutboxWriter outbox, ClientIpResolver ipResolver, Clock clock,
                         @Value("${spring.application.name}") String serviceName) {
        this.outbox = outbox;
        this.ipResolver = ipResolver;
        this.clock = clock;
        this.serviceName = serviceName;
    }

    public void success(Actor actor, String action, String resourceType, UUID resourceId,
                        Map<String, Object> before, Map<String, Object> after) {
        record(actor, action, resourceType, resourceId, before, after, AuditEvent.OUTCOME_SUCCESS);
    }

    public void failure(Actor actor, String action, String resourceType, UUID resourceId,
                        Map<String, Object> before, Map<String, Object> after) {
        record(actor, action, resourceType, resourceId, before, after, AuditEvent.OUTCOME_FAILURE);
    }

    private void record(Actor actor, String action, String resourceType, UUID resourceId,
                        Map<String, Object> before, Map<String, Object> after, String outcome) {
        AuditEvent event = new AuditEvent(
                Events.newId(), AuditEvent.TYPE, Events.VERSION_1, clock.instant(),
                actor.userId(), actor.username(), actor.role(),
                action, resourceType, resourceId.toString(),
                before, after,
                CorrelationId.current(), ipResolver.currentIp(), serviceName, outcome);
        outbox.append(resourceType, resourceId, Topics.AUDIT_EVENT, resourceId.toString(), event);
    }
}
