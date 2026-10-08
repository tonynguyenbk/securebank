package com.securebank.fraud;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.securebank.common.events.Events;
import com.securebank.common.events.Topics;
import com.securebank.common.events.TransactionCompletedEvent;
import com.securebank.common.json.EventJson;
import com.securebank.common.security.Role;
import com.securebank.fraud.application.FraudDetectionService;
import com.securebank.fraud.support.IntegrationTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class FraudServiceIntegrationTest extends IntegrationTestSupport {

    private static final UUID STAFF_ID = UUID.fromString("00000000-0000-4000-8000-000000000201");
    private static final UUID AUDITOR_ID = UUID.fromString("00000000-0000-4000-8000-000000000301");

    @Autowired
    MockMvc mvc;
    @Autowired
    KafkaTemplate<String, String> kafka;
    @Autowired
    EventJson eventJson;
    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    FraudDetectionService detection;
    @Autowired
    ObjectMapper objectMapper;

    private final String staff = bearer(STAFF_ID, "staff1", Role.BANK_STAFF);
    private final String auditor = bearer(AUDITOR_ID, "auditor1", Role.AUDITOR);

    private static TransactionCompletedEvent transfer(UUID customerId, UUID sourceAccountId, UUID destinationAccountId,
                                                      long amount) {
        return new TransactionCompletedEvent(Events.newId(), TransactionCompletedEvent.TYPE, Events.VERSION_1,
                Instant.now(), UUID.randomUUID(), "TX" + System.nanoTime(), sourceAccountId, "1000000001",
                destinationAccountId, "1000000002", customerId, "Nguyen Van An", UUID.randomUUID(),
                UUID.randomUUID(), "Tran Thi Binh", UUID.randomUUID(), BigDecimal.valueOf(amount).setScale(2), "VND",
                "test", BigDecimal.valueOf(1_000_000_000L), BigDecimal.valueOf(5_000_000L));
    }

    private void publish(TransactionCompletedEvent e) throws Exception {
        kafka.send(Topics.TRANSACTION_COMPLETED, e.sourceAccountId().toString(), eventJson.write(e)).get();
    }

    private boolean processed(UUID eventId) {
        Integer n = jdbc.queryForObject("SELECT count(*) FROM processed_events WHERE event_id = ? AND consumer = ?",
                Integer.class, eventId, FraudDetectionService.CONSUMER_NAME);
        return n != null && n > 0;
    }

    private int alertCount(UUID transactionId) {
        Integer n = jdbc.queryForObject("SELECT count(*) FROM fraud_alerts WHERE transaction_id = ?", Integer.class,
                transactionId);
        return n == null ? 0 : n;
    }

    private JsonNode json(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private JsonNode listAlertsOf(UUID customerId) throws Exception {
        return json(mvc.perform(get("/api/v1/fraud/alerts").param("customerId", customerId.toString())
                        .header("Authorization", staff))
                .andExpect(status().isOk()).andReturn());
    }

    @Test
    void highAmountTransferFromKafkaCreatesHighAlertWithTwoRulesAndOutboxEvents() throws Exception {
        UUID customer = UUID.randomUUID();
        TransactionCompletedEvent event = transfer(customer, UUID.randomUUID(), UUID.randomUUID(), 100_000_000L);

        publish(event);

        await().atMost(Duration.ofSeconds(30)).until(() -> alertCount(event.transactionId()) == 1);
        JsonNode page = listAlertsOf(customer);
        assertThat(page.get("totalElements").asInt()).isEqualTo(1);
        JsonNode summary = page.get("content").get(0);
        assertThat(summary.get("riskScore").asInt()).isEqualTo(60);
        assertThat(summary.get("riskLevel").asText()).isEqualTo("HIGH");
        assertThat(summary.get("status").asText()).isEqualTo("OPEN");
        assertThat(summary.get("amount").decimalValue()).isEqualByComparingTo("100000000");

        String alertId = summary.get("id").asText();
        mvc.perform(get("/api/v1/fraud/alerts/{id}", alertId).header("Authorization", auditor))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rules.length()").value(2))
                .andExpect(jsonPath("$.rules[0].ruleCode").value("HIGH_AMOUNT"))
                .andExpect(jsonPath("$.rules[0].scoreContribution").value(40))
                .andExpect(jsonPath("$.rules[1].ruleCode").value("NEW_BENEFICIARY"))
                .andExpect(jsonPath("$.rules[1].scoreContribution").value(20))
                .andExpect(jsonPath("$.timeline.length()").value(1))
                .andExpect(jsonPath("$.timeline[0].status").value("OPEN"))
                .andExpect(jsonPath("$.destinationAccountNumber").value("1000000002"))
                .andExpect(jsonPath("$.reviewedBy").isEmpty());

        List<String> outboxTypes = jdbc.queryForList(
                "SELECT event_type FROM outbox_events WHERE aggregate_id = ? ORDER BY event_type", String.class,
                UUID.fromString(alertId));
        assertThat(outboxTypes).containsExactly("AUDIT", "FRAUD_ALERT_CREATED");
        String auditPayload = jdbc.queryForObject("""
                SELECT payload::text FROM outbox_events WHERE aggregate_id = ? AND event_type = 'AUDIT'
                """, String.class, UUID.fromString(alertId));
        JsonNode audit = objectMapper.readTree(auditPayload);
        assertThat(audit.get("action").asText()).isEqualTo("FRAUD_ALERT_CREATED");
        assertThat(audit.get("actorUserId").isNull()).isTrue();
        assertThat(audit.get("sourceService").asText()).isEqualTo("fraud-service");
        String createdPayload = jdbc.queryForObject("""
                SELECT payload::text FROM outbox_events WHERE aggregate_id = ? AND event_type = 'FRAUD_ALERT_CREATED'
                """, String.class, UUID.fromString(alertId));
        assertThat(objectMapper.readTree(createdPayload).get("riskLevel").asText()).isEqualTo("HIGH");
        assertThat(jdbc.queryForObject("SELECT event_key FROM outbox_events WHERE aggregate_id = ? AND event_type = 'FRAUD_ALERT_CREATED'",
                String.class, UUID.fromString(alertId))).isEqualTo(customer.toString());
    }

    @Test
    void sameEventDeliveredTwiceCreatesOneAlert() throws Exception {
        UUID customer = UUID.randomUUID();
        UUID sourceAccount = UUID.randomUUID();
        TransactionCompletedEvent event = transfer(customer, sourceAccount, UUID.randomUUID(), 120_000_000L);
        // marker on the same key → same partition → processed after both copies
        TransactionCompletedEvent marker = transfer(customer, sourceAccount, UUID.randomUUID(), 1_000L);

        publish(event);
        publish(event);
        publish(marker);

        await().atMost(Duration.ofSeconds(30)).until(() -> processed(marker.eventId()));
        assertThat(alertCount(event.transactionId())).isEqualTo(1);
        assertThat(listAlertsOf(customer).get("totalElements").asInt()).isEqualTo(1);
        Integer outboxRows = jdbc.queryForObject("""
                SELECT count(*) FROM outbox_events o JOIN fraud_alerts a ON a.id = o.aggregate_id
                WHERE a.transaction_id = ?
                """, Integer.class, event.transactionId());
        assertThat(outboxRows).isEqualTo(2);
    }

    @Test
    void sixQuickTransfersTriggerHighFrequency() throws Exception {
        UUID customer = UUID.randomUUID();
        UUID sourceAccount = UUID.randomUUID();
        UUID destination = UUID.randomUUID();
        List<TransactionCompletedEvent> events = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            events.add(transfer(customer, sourceAccount, destination, 1_000_000L));
        }
        for (TransactionCompletedEvent e : events) {
            publish(e);
        }

        TransactionCompletedEvent sixth = events.getLast();
        await().atMost(Duration.ofSeconds(30)).until(() -> processed(sixth.eventId()));
        for (int i = 0; i < 5; i++) {
            assertThat(alertCount(events.get(i).transactionId())).isZero();
        }
        assertThat(alertCount(sixth.transactionId())).isEqualTo(1);
        JsonNode summary = listAlertsOf(customer).get("content").get(0);
        assertThat(summary.get("riskScore").asInt()).isEqualTo(30);
        assertThat(summary.get("riskLevel").asText()).isEqualTo("MEDIUM");
        mvc.perform(get("/api/v1/fraud/alerts/{id}", summary.get("id").asText()).header("Authorization", staff))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rules.length()").value(1))
                .andExpect(jsonPath("$.rules[0].ruleCode").value("HIGH_FREQUENCY"));
    }

    @Test
    void knownBeneficiaryIsNotNewAgainEvenAfterRedisCacheLoss() {
        UUID customer = UUID.randomUUID();
        UUID destination = UUID.randomUUID();
        // first transfer: new beneficiary (20) only → LOW, no alert, but the pair is now recorded
        assertThat(detection.process(transfer(customer, UUID.randomUUID(), destination, 20_000_000L))).isEmpty();
        // HIGH_AMOUNT alone (40) → MEDIUM; NEW_BENEFICIARY must not fire for the same destination
        TransactionCompletedEvent second = transfer(customer, UUID.randomUUID(), destination, 100_000_000L);
        UUID alertId = detection.process(second).orElseThrow();
        Integer score = jdbc.queryForObject("SELECT risk_score FROM fraud_alerts WHERE id = ?", Integer.class, alertId);
        assertThat(score).isEqualTo(40);
    }

    @Test
    void reviewWorkflowRolesTransitionsAndAudit() throws Exception {
        UUID alertId = detection.process(transfer(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                150_000_000L)).orElseThrow();
        String reviewPath = "/api/v1/fraud/alerts/" + alertId + "/review";

        // unauthenticated → 401, CUSTOMER → 403 everywhere, AUDITOR read-only → 403 on review
        mvc.perform(get("/api/v1/fraud/alerts")).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
        String customer = bearer(UUID.randomUUID(), "customer1", Role.CUSTOMER);
        mvc.perform(get("/api/v1/fraud/alerts").header("Authorization", customer))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("FORBIDDEN_OPERATION"));
        mvc.perform(get("/api/v1/fraud/alerts/stats").header("Authorization", customer))
                .andExpect(status().isForbidden());
        mvc.perform(patch(reviewPath).header("Authorization", auditor).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"UNDER_REVIEW\"}"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("FORBIDDEN_OPERATION"));

        // OPEN → UNDER_REVIEW (note optional)
        mvc.perform(patch(reviewPath).header("Authorization", staff).header("X-Forwarded-For", "203.0.113.7, 10.0.0.1")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"UNDER_REVIEW\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UNDER_REVIEW"))
                .andExpect(jsonPath("$.reviewedByUsername").value("staff1"))
                .andExpect(jsonPath("$.reviewedBy").value(STAFF_ID.toString()))
                .andExpect(jsonPath("$.timeline.length()").value(2))
                .andExpect(jsonPath("$.timeline[1].actorUsername").value("staff1"));

        // REJECTED without note → 400
        mvc.perform(patch(reviewPath).header("Authorization", staff).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"REJECTED\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        // missing status → 400
        mvc.perform(patch(reviewPath).header("Authorization", staff).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"note\":\"x\"}"))
                .andExpect(status().isBadRequest());

        // UNDER_REVIEW → REJECTED with note
        mvc.perform(patch(reviewPath).header("Authorization", staff).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"REJECTED\",\"note\":\"Customer confirmed fraud\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.reviewNote").value("Customer confirmed fraud"));

        // REJECTED → UNDER_REVIEW is not allowed → 409
        mvc.perform(patch(reviewPath).header("Authorization", staff).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"UNDER_REVIEW\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("FRAUD_ALERT_INVALID_TRANSITION"));

        // REJECTED → CLOSED by an ADMIN; CLOSED is terminal
        String admin = bearer(UUID.randomUUID(), "admin1", Role.ADMIN);
        mvc.perform(patch(reviewPath).header("Authorization", admin).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"CLOSED\",\"note\":\"Account frozen, case closed\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CLOSED"))
                .andExpect(jsonPath("$.timeline.length()").value(4));
        mvc.perform(patch(reviewPath).header("Authorization", admin).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"APPROVED\",\"note\":\"x\"}"))
                .andExpect(status().isConflict());

        // audit events written through the outbox: before/after, actor and client IP
        List<String> reviews = jdbc.queryForList("""
                SELECT payload::text FROM outbox_events
                WHERE aggregate_id = ? AND payload->>'action' = 'FRAUD_ALERT_REVIEW' ORDER BY created_at
                """, String.class, alertId);
        assertThat(reviews).hasSize(3);
        JsonNode first = objectMapper.readTree(reviews.getFirst());
        assertThat(first.get("before").get("status").asText()).isEqualTo("OPEN");
        assertThat(first.get("after").get("status").asText()).isEqualTo("UNDER_REVIEW");
        assertThat(first.get("actorUsername").asText()).isEqualTo("staff1");
        assertThat(first.get("actorRole").asText()).isEqualTo("BANK_STAFF");
        assertThat(first.get("ipAddress").asText()).isEqualTo("203.0.113.7");
        assertThat(first.get("resourceType").asText()).isEqualTo("FRAUD_ALERT");
    }

    @Test
    void unknownAlertIs404AndStatsAreReadable() throws Exception {
        mvc.perform(get("/api/v1/fraud/alerts/{id}", UUID.randomUUID()).header("Authorization", staff))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("FRAUD_ALERT_NOT_FOUND"));
        detection.process(transfer(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), 100_000_000L));
        mvc.perform(get("/api/v1/fraud/alerts/stats").header("Authorization", auditor))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.open").isNumber())
                .andExpect(jsonPath("$.createdToday").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.underReview").isNumber())
                .andExpect(jsonPath("$.critical").isNumber())
                .andExpect(jsonPath("$.high").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)));
        mvc.perform(get("/api/v1/fraud/alerts").param("sort", "password,asc").header("Authorization", staff))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/fraud/alerts").param("riskLevel", "HIGH").param("status", "OPEN")
                        .param("sort", "riskScore,desc").header("Authorization", staff))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].riskLevel").value("HIGH"));
    }
}
