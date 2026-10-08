package com.securebank.notification;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.securebank.common.events.AccountStatusChangedEvent;
import com.securebank.common.events.Events;
import com.securebank.common.events.Topics;
import com.securebank.common.events.TransactionCompletedEvent;
import com.securebank.common.events.TransactionFailedEvent;
import com.securebank.common.events.UserRegisteredEvent;
import com.securebank.common.json.EventJson;
import com.securebank.common.security.Role;
import com.securebank.notification.support.IntegrationTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class NotificationServiceIntegrationTest extends IntegrationTestSupport {

    @Autowired
    MockMvc mvc;
    @Autowired
    KafkaTemplate<String, String> kafka;
    @Autowired
    EventJson eventJson;
    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    ObjectMapper objectMapper;

    private TransactionCompletedEvent completed(UUID senderUser, UUID recipientUser, UUID sourceAccount) {
        return new TransactionCompletedEvent(Events.newId(), TransactionCompletedEvent.TYPE, 1, Instant.now(),
                UUID.randomUUID(), "TX" + System.nanoTime(), sourceAccount, "1000000001", UUID.randomUUID(),
                "1000000002", UUID.randomUUID(), "Nguyen Van An", senderUser, UUID.randomUUID(), "Tran Thi Binh",
                recipientUser, new BigDecimal("123456789.12"), "VND", "Rent",
                new BigDecimal("24000000.00"), new BigDecimal("11000000.00"));
    }

    private int countFor(UUID eventId) {
        Integer n = jdbc.queryForObject("SELECT count(*) FROM notifications WHERE source_event_id = ?", Integer.class,
                eventId);
        return n == null ? 0 : n;
    }

    private void publish(String topic, String key, Object event) throws Exception {
        kafka.send(topic, key, eventJson.write(event)).get();
    }

    @Test
    void completedTransferNotifiesSenderAndRecipientOnceEvenIfDuplicated() throws Exception {
        UUID senderUser = UUID.randomUUID();
        UUID recipientUser = UUID.randomUUID();
        UUID sourceAccount = UUID.randomUUID();
        TransactionCompletedEvent e = completed(senderUser, recipientUser, sourceAccount);
        TransactionCompletedEvent marker = completed(UUID.randomUUID(), UUID.randomUUID(), sourceAccount);

        publish(Topics.TRANSACTION_COMPLETED, sourceAccount.toString(), e);
        publish(Topics.TRANSACTION_COMPLETED, sourceAccount.toString(), e);
        publish(Topics.TRANSACTION_COMPLETED, sourceAccount.toString(), marker); // same partition, after both copies

        await().atMost(Duration.ofSeconds(30)).until(() -> countFor(marker.eventId()) == 4);
        assertThat(countFor(e.eventId())).isEqualTo(4);

        String senderToken = bearer(senderUser, "customer1", Role.CUSTOMER);
        JsonNode senderPage = objectMapper.readTree(mvc.perform(get("/api/v1/notifications/me")
                        .header("Authorization", senderToken))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(senderPage.get("totalElements").asInt()).isEqualTo(2);
        for (JsonNode n : senderPage.get("content")) {
            assertThat(n.get("templateCode").asText()).isEqualTo("TRANSFER_SENT");
            assertThat(n.get("status").asText()).isEqualTo("SENT");
            assertThat(n.get("sentAt").isNull()).isFalse();
            assertThat(n.get("read").asBoolean()).isFalse();
            assertThat(n.get("relatedTransactionId").asText()).isEqualTo(e.transactionId().toString());
            assertThat(n.get("params").get("accountNumber").asText()).isEqualTo("******0001");
            assertThat(n.get("params").get("amount").decimalValue()).isEqualByComparingTo("123456789.12");
            assertThat(n.get("message").asText()).contains("123,456,789.12 VND").contains("Tran Thi Binh");
        }
        assertThat(senderPage.get("content").findValuesAsText("channel")).containsExactlyInAnyOrder("IN_APP", "EMAIL");

        String recipientToken = bearer(recipientUser, "customer2", Role.CUSTOMER);
        mvc.perform(get("/api/v1/notifications/me").param("channel", "SMS").header("Authorization", recipientToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].templateCode").value("TRANSFER_RECEIVED"))
                .andExpect(jsonPath("$.content[0].params.counterpartyName").value("Nguyen Van An"));
        mvc.perform(get("/api/v1/notifications/me/unread-count").header("Authorization", recipientToken))
                .andExpect(jsonPath("$.count").value(1));
    }

    @Test
    void meReturnsOnlyOwnNotificationsAndMarkReadIsOwnerOnly() throws Exception {
        UUID alice = UUID.randomUUID();
        UUID bob = UUID.randomUUID();
        UserRegisteredEvent welcomeAlice = new UserRegisteredEvent(Events.newId(), UserRegisteredEvent.TYPE, 1,
                Instant.now(), alice, "alice", "Alice Nguyen", "alice@example.com", null);
        UserRegisteredEvent welcomeBob = new UserRegisteredEvent(Events.newId(), UserRegisteredEvent.TYPE, 1,
                Instant.now(), bob, "bob", "Bob Tran", "bob@example.com", null);
        publish(Topics.USER_REGISTERED, alice.toString(), welcomeAlice);
        publish(Topics.USER_REGISTERED, bob.toString(), welcomeBob);
        await().atMost(Duration.ofSeconds(30))
                .until(() -> countFor(welcomeAlice.eventId()) == 1 && countFor(welcomeBob.eventId()) == 1);

        String aliceToken = bearer(alice, "alice", Role.CUSTOMER);
        String bobToken = bearer(bob, "bob", Role.CUSTOMER);
        JsonNode alicePage = objectMapper.readTree(mvc.perform(get("/api/v1/notifications/me")
                        .header("Authorization", aliceToken))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(alicePage.get("totalElements").asInt()).isEqualTo(1);
        assertThat(alicePage.get("content").get(0).get("templateCode").asText()).isEqualTo("WELCOME");
        assertThat(alicePage.get("content").get(0).get("message").asText()).contains("Alice Nguyen");
        String aliceNotification = alicePage.get("content").get(0).get("id").asText();

        // bob cannot see or touch alice's notification
        mvc.perform(patch("/api/v1/notifications/{id}/read", aliceNotification).header("Authorization", bobToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOTIFICATION_NOT_FOUND"));
        mvc.perform(get("/api/v1/notifications/me/unread-count").header("Authorization", aliceToken))
                .andExpect(jsonPath("$.count").value(1));

        mvc.perform(patch("/api/v1/notifications/{id}/read", aliceNotification).header("Authorization", aliceToken))
                .andExpect(status().isNoContent());
        mvc.perform(patch("/api/v1/notifications/{id}/read", aliceNotification).header("Authorization", aliceToken))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/notifications/me/unread-count").header("Authorization", aliceToken))
                .andExpect(jsonPath("$.count").value(0));
        mvc.perform(get("/api/v1/notifications/me").param("unreadOnly", "true").header("Authorization", aliceToken))
                .andExpect(jsonPath("$.totalElements").value(0));
        mvc.perform(get("/api/v1/notifications/me").header("Authorization", aliceToken))
                .andExpect(jsonPath("$.content[0].read").value(true));

        mvc.perform(patch("/api/v1/notifications/{id}/read", UUID.randomUUID()).header("Authorization", aliceToken))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/notifications/me")).andExpect(status().isUnauthorized());
        // staff accounts are users too: they simply have no notifications
        mvc.perform(get("/api/v1/notifications/me").header("Authorization",
                        bearer(UUID.randomUUID(), "staff1", Role.BANK_STAFF)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void failedTransferAndFreezeProduceNotifications() throws Exception {
        UUID user = UUID.randomUUID();
        UUID account = UUID.randomUUID();
        var failed = new TransactionFailedEvent(Events.newId(), TransactionFailedEvent.TYPE, 1, Instant.now(),
                UUID.randomUUID(), "TX9", account, "1000000001", "1000000002", UUID.randomUUID(), user,
                new BigDecimal("50000000.00"), "VND", "REJECTED", "INSUFFICIENT_FUNDS", "Insufficient funds.");
        var frozen = new AccountStatusChangedEvent(Events.newId(), AccountStatusChangedEvent.TYPE, 1, Instant.now(),
                account, "1000000001", UUID.randomUUID(), user, "ACTIVE", "FROZEN", "Suspected fraud TX9",
                UUID.randomUUID());
        publish(Topics.TRANSACTION_FAILED, account.toString(), failed);
        publish(Topics.ACCOUNT_STATUS_CHANGED, account.toString(), frozen);
        await().atMost(Duration.ofSeconds(30))
                .until(() -> countFor(failed.eventId()) == 1 && countFor(frozen.eventId()) == 2);

        String token = bearer(user, "customer1", Role.CUSTOMER);
        mvc.perform(get("/api/v1/notifications/me").param("channel", "IN_APP").header("Authorization", token))
                .andExpect(jsonPath("$.totalElements").value(2));
        String body = mvc.perform(get("/api/v1/notifications/me").header("Authorization", token))
                .andReturn().getResponse().getContentAsString();
        assertThat(body).contains("TRANSFER_REJECTED").contains("ACCOUNT_FROZEN").contains("INSUFFICIENT_FUNDS")
                .doesNotContain("Suspected fraud");
    }
}
