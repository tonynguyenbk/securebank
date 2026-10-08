package com.securebank.audit;

import com.securebank.audit.application.AuditIngestService;
import com.securebank.audit.support.IntegrationTestSupport;
import com.securebank.common.events.AuditEvent;
import com.securebank.common.events.Events;
import com.securebank.common.events.Topics;
import com.securebank.common.json.EventJson;
import com.securebank.common.security.Role;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuditServiceIntegrationTest extends IntegrationTestSupport {

    private static final UUID STAFF_ID = UUID.fromString("00000000-0000-4000-8000-000000000201");

    @Autowired
    MockMvc mvc;
    @Autowired
    KafkaTemplate<String, String> kafka;
    @Autowired
    EventJson eventJson;
    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    AuditIngestService ingest;

    private final String auditor = bearer(UUID.randomUUID(), "auditor1", Role.AUDITOR);
    private final String admin = bearer(UUID.randomUUID(), "admin1", Role.ADMIN);
    private final String staff = bearer(STAFF_ID, "staff1", Role.BANK_STAFF);

    private static AuditEvent event(String action, String resourceType, String resourceId, String actorUsername,
                                    String correlationId, Instant occurredAt) {
        return new AuditEvent(Events.newId(), AuditEvent.TYPE, Events.VERSION_1, occurredAt, STAFF_ID, actorUsername,
                "BANK_STAFF", action, resourceType, resourceId, Map.of("status", "ACTIVE"),
                Map.of("status", "FROZEN", "reason", "Suspected fraud"), correlationId, "172.18.0.1",
                "banking-core-service", AuditEvent.OUTCOME_SUCCESS);
    }

    private int storedCount(UUID eventId) {
        Integer n = jdbc.queryForObject("SELECT count(*) FROM audit_logs WHERE event_id = ?", Integer.class, eventId);
        return n == null ? 0 : n;
    }

    private void publish(String key, String payload) throws Exception {
        kafka.send(Topics.AUDIT_EVENT, key, payload).get();
    }

    @Test
    void consumedEventIsStoredOnceEvenIfDeliveredTwice() throws Exception {
        String resourceId = UUID.randomUUID().toString();
        AuditEvent e = event("ACCOUNT_FREEZE", "ACCOUNT", resourceId, "staff1", "corr-" + UUID.randomUUID(), Instant.now());
        AuditEvent marker = event("ACCOUNT_UNFREEZE", "ACCOUNT", resourceId, "staff1", null, Instant.now());

        publish(resourceId, eventJson.write(e));
        publish(resourceId, eventJson.write(e));
        publish(resourceId, eventJson.write(marker)); // same key → same partition → consumed after both copies

        await().atMost(Duration.ofSeconds(30)).until(() -> storedCount(marker.eventId()) == 1);
        assertThat(storedCount(e.eventId())).isEqualTo(1);

        String id = jdbc.queryForObject("SELECT id::text FROM audit_logs WHERE event_id = ?", String.class, e.eventId());
        mvc.perform(get("/api/v1/audit/logs/{id}", id).header("Authorization", auditor))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.action").value("ACCOUNT_FREEZE"))
                .andExpect(jsonPath("$.before.status").value("ACTIVE"))
                .andExpect(jsonPath("$.after.status").value("FROZEN"))
                .andExpect(jsonPath("$.after.reason").value("Suspected fraud"))
                .andExpect(jsonPath("$.ipAddress").value("172.18.0.1"))
                .andExpect(jsonPath("$.sourceService").value("banking-core-service"))
                .andExpect(jsonPath("$.receivedAt").isNotEmpty());
    }

    @Test
    void poisonMessageIsSkippedAndConsumptionContinues() throws Exception {
        String key = UUID.randomUUID().toString();
        AuditEvent after = event("ACCOUNT_FREEZE", "ACCOUNT", key, "staff1", null, Instant.now());
        publish(key, "{not json");
        publish(key, "{\"eventId\":\"" + UUID.randomUUID() + "\"}"); // missing required fields
        publish(key, eventJson.write(after));
        await().atMost(Duration.ofSeconds(30)).until(() -> storedCount(after.eventId()) == 1);
    }

    @Test
    void auditLogsAreAppendOnlyAtDatabaseLevel() {
        AuditEvent e = event("TRANSFER_LIMIT_UPDATE", "ACCOUNT", UUID.randomUUID().toString(), "staff1", null, Instant.now());
        assertThat(ingest.record(e)).isTrue();
        assertThat(ingest.record(e)).isFalse();

        assertThatThrownBy(() -> jdbc.update("UPDATE audit_logs SET action = 'TAMPERED' WHERE event_id = ?", e.eventId()))
                .isInstanceOf(DataIntegrityViolationException.class)
                .rootCause().hasMessageContaining("append-only");
        assertThatThrownBy(() -> jdbc.update("DELETE FROM audit_logs WHERE event_id = ?", e.eventId()))
                .isInstanceOf(DataIntegrityViolationException.class)
                .rootCause().hasMessageContaining("append-only");
        assertThatThrownBy(() -> jdbc.execute("TRUNCATE audit_logs"))
                .isInstanceOf(DataAccessException.class);
        assertThat(jdbc.queryForObject("SELECT action FROM audit_logs WHERE event_id = ?", String.class, e.eventId()))
                .isEqualTo("TRANSFER_LIMIT_UPDATE");
    }

    @Test
    void filtersWork() throws Exception {
        String corr = "filter-" + UUID.randomUUID();
        String account = UUID.randomUUID().toString();
        Instant t0 = Instant.parse("2026-01-10T10:00:00Z");
        ingest.record(event("ACCOUNT_FREEZE", "ACCOUNT", account, "staff_alice", corr, t0));
        ingest.record(event("ACCOUNT_UNFREEZE", "ACCOUNT", account, "staff_alice", corr, t0.plusSeconds(3600)));
        ingest.record(event("LOGIN_SUCCESS", "USER", UUID.randomUUID().toString(), "bob", corr, t0.plusSeconds(7200)));

        mvc.perform(get("/api/v1/audit/logs").param("correlationId", corr).header("Authorization", auditor))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(3))
                // newest first
                .andExpect(jsonPath("$.content[0].action").value("LOGIN_SUCCESS"))
                .andExpect(jsonPath("$.content[2].action").value("ACCOUNT_FREEZE"));
        mvc.perform(get("/api/v1/audit/logs").param("correlationId", corr).param("actor", "ALICE")
                        .header("Authorization", auditor))
                .andExpect(jsonPath("$.totalElements").value(2));
        mvc.perform(get("/api/v1/audit/logs").param("correlationId", corr).param("action", "ACCOUNT_UNFREEZE")
                        .header("Authorization", auditor))
                .andExpect(jsonPath("$.totalElements").value(1));
        mvc.perform(get("/api/v1/audit/logs").param("resourceType", "ACCOUNT").param("resourceId", account)
                        .header("Authorization", admin))
                .andExpect(jsonPath("$.totalElements").value(2));
        mvc.perform(get("/api/v1/audit/logs").param("correlationId", corr)
                        .param("from", "2026-01-10T10:30:00Z").param("to", "2026-01-10T12:00:00Z")
                        .header("Authorization", auditor))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].action").value("ACCOUNT_UNFREEZE"));
        mvc.perform(get("/api/v1/audit/logs").param("correlationId", corr).param("actorUserId", STAFF_ID.toString())
                        .param("size", "2").header("Authorization", auditor))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.content.length()").value(2));
        mvc.perform(get("/api/v1/audit/logs").param("actor", "%").param("correlationId", corr)
                        .header("Authorization", auditor))
                .andExpect(jsonPath("$.totalElements").value(0));
        mvc.perform(get("/api/v1/audit/logs").param("size", "101").header("Authorization", auditor))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/audit/logs/actions").header("Authorization", auditor))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasItem("LOGIN_SUCCESS")))
                .andExpect(jsonPath("$", hasItem("ACCOUNT_FREEZE")));
    }

    @Test
    void bankStaffSeesRestrictedViewAndCustomerIsForbidden() throws Exception {
        String corr = "restricted-" + UUID.randomUUID();
        AuditEvent accountEvent = event("ACCOUNT_FREEZE", "ACCOUNT", UUID.randomUUID().toString(), "staff1", corr,
                Instant.now());
        AuditEvent userEvent = event("LOGIN_FAILURE", "USER", UUID.randomUUID().toString(), "someone", corr,
                Instant.now());
        ingest.record(accountEvent);
        ingest.record(userEvent);
        String userLogId = jdbc.queryForObject("SELECT id::text FROM audit_logs WHERE event_id = ?", String.class,
                userEvent.eventId());
        String accountLogId = jdbc.queryForObject("SELECT id::text FROM audit_logs WHERE event_id = ?", String.class,
                accountEvent.eventId());

        mvc.perform(get("/api/v1/audit/logs").param("correlationId", corr).header("Authorization", staff))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].resourceType").value("ACCOUNT"))
                .andExpect(jsonPath("$.content[0].ipAddress").doesNotExist());
        // asking for a hidden resource type explicitly still yields nothing
        mvc.perform(get("/api/v1/audit/logs").param("correlationId", corr).param("resourceType", "USER")
                        .header("Authorization", staff))
                .andExpect(jsonPath("$.totalElements").value(0));
        mvc.perform(get("/api/v1/audit/logs/{id}", userLogId).header("Authorization", staff))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("AUDIT_LOG_NOT_FOUND"));
        mvc.perform(get("/api/v1/audit/logs/{id}", accountLogId).header("Authorization", staff))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ipAddress").doesNotExist())
                .andExpect(jsonPath("$.after.status").value("FROZEN"));
        mvc.perform(get("/api/v1/audit/logs/actions").header("Authorization", staff))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", not(hasItem("LOGIN_FAILURE"))));

        // full view keeps the IP
        mvc.perform(get("/api/v1/audit/logs").param("correlationId", corr).header("Authorization", auditor))
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].ipAddress").value("172.18.0.1"));

        String customer = bearer(UUID.randomUUID(), "customer1", Role.CUSTOMER);
        mvc.perform(get("/api/v1/audit/logs").header("Authorization", customer))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN_OPERATION"));
        mvc.perform(get("/api/v1/audit/logs/{id}", accountLogId).header("Authorization", customer))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/audit/logs/actions").header("Authorization", customer))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/audit/logs")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/audit/logs/{id}", UUID.randomUUID()).header("Authorization", auditor))
                .andExpect(status().isNotFound());
    }
}
