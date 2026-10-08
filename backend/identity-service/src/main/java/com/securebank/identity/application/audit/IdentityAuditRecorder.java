package com.securebank.identity.application.audit;

import com.securebank.common.events.AuditEvent;
import com.securebank.common.events.Events;
import com.securebank.common.events.Topics;
import com.securebank.common.outbox.OutboxWriter;
import com.securebank.common.security.Role;
import com.securebank.common.web.CorrelationId;
import com.securebank.identity.domain.User;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

/** Appends identity audit events to the outbox, inside the caller's transaction (events.md). */
@Component
public class IdentityAuditRecorder {

    public static final String SOURCE_SERVICE = "identity-service";
    public static final String RESOURCE_USER = "USER";

    public static final String USER_REGISTERED = "USER_REGISTERED";
    public static final String LOGIN_SUCCESS = "LOGIN_SUCCESS";
    public static final String LOGIN_FAILURE = "LOGIN_FAILURE";
    public static final String LOGOUT = "LOGOUT";

    private final OutboxWriter outboxWriter;

    public IdentityAuditRecorder(OutboxWriter outboxWriter) {
        this.outboxWriter = outboxWriter;
    }

    /**
     * @param actor          the acting user, or null when unknown (e.g. login with a non-existent username)
     * @param resourceUserId the user the action is about (usually the actor), or null
     * @param after          extra context; never contains secrets
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void record(String action, String outcome, User actor, UUID resourceUserId, Map<String, Object> after,
                       String ipAddress) {
        Role role = actor == null ? null : actor.primaryRole();
        String resourceId = resourceUserId == null ? null : resourceUserId.toString();
        AuditEvent event = new AuditEvent(
                Events.newId(), AuditEvent.TYPE, Events.VERSION_1, Events.now(),
                actor == null ? null : actor.getId(),
                actor == null ? null : actor.getUsername(),
                role == null ? null : role.name(),
                action, RESOURCE_USER, resourceId,
                null, after == null || after.isEmpty() ? null : after,
                CorrelationId.current(), ipAddress, SOURCE_SERVICE, outcome);
        outboxWriter.append(RESOURCE_USER, resourceUserId, Topics.AUDIT_EVENT, resourceId, event);
    }
}
