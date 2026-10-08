package com.securebank.bankingcore.integration;

import com.jayway.jsonpath.JsonPath;
import com.securebank.bankingcore.support.IntegrationTestSupport;
import com.securebank.bankingcore.support.TestBank.TestAccount;
import com.securebank.common.events.Events;
import com.securebank.common.events.Topics;
import com.securebank.common.events.TransactionCompletedEvent;
import com.securebank.common.events.UserRegisteredEvent;
import com.securebank.common.json.EventJson;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static com.securebank.bankingcore.support.TransferRequests.newKey;
import static com.securebank.bankingcore.support.TransferRequests.transfer;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Spec §37: the outbox really publishes to Kafka, and the UserRegistered consumer is idempotent. */
class KafkaIntegrationTest extends IntegrationTestSupport {

    @Autowired
    private KafkaTemplate<String, String> kafka;

    @Autowired
    private EventJson json;

    @Test
    void outboxPublisherPublishesTransactionCompletedEvent() throws Exception {
        TestAccount a = bank.open("Nguyễn Kafka", "5000000.00");
        TestAccount b = bank.open("Trần Kafka", "0.00");
        String txId = JsonPath.read(mvc.perform(transfer(customerToken(a), newKey(), a.accountNumber(),
                        b.accountNumber(), "1000000", "Demo transfer"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.transactionId");

        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers(),
                ConsumerConfig.GROUP_ID_CONFIG, "test-" + UUID.randomUUID(),
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class))) {
            consumer.subscribe(List.of(Topics.TRANSACTION_COMPLETED));
            ConsumerRecord<String, String> record = await().atMost(Duration.ofSeconds(30)).until(
                    () -> find(consumer, txId), Optional::isPresent).orElseThrow();

            assertThat(record.key()).isEqualTo(a.accountId().toString());
            assertThat(new String(record.headers().lastHeader("eventType").value(), StandardCharsets.UTF_8))
                    .isEqualTo(TransactionCompletedEvent.TYPE);
            TransactionCompletedEvent event = json.read(record.value(), TransactionCompletedEvent.class);
            assertThat(event.eventType()).isEqualTo("TRANSACTION_COMPLETED");
            assertThat(event.sourceAccountNumber()).isEqualTo(a.accountNumber());
            assertThat(event.customerName()).isEqualTo("Nguyễn Kafka");
            assertThat(event.sourceUserId()).isEqualTo(a.userId());
            assertThat(event.destinationCustomerName()).isEqualTo("Trần Kafka");
            assertThat(event.destinationUserId()).isEqualTo(b.userId());
            assertThat(event.amount()).isEqualByComparingTo("1000000");
            assertThat(event.sourceBalanceAfter()).isEqualByComparingTo("4000000");
            assertThat(event.destinationBalanceAfter()).isEqualByComparingTo("1000000");
            assertThat(event.description()).isEqualTo("Demo transfer");
        }
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> assertThat(jdbc.queryForObject("""
                SELECT status FROM outbox_events WHERE aggregate_id = ?::uuid AND event_type = 'TRANSACTION_COMPLETED'
                """, String.class, txId)).isEqualTo("PUBLISHED"));
    }

    @Test
    void userRegisteredOpensExactlyOneAccountEvenWhenDeliveredTwice() throws Exception {
        UUID userId = UUID.randomUUID();
        UserRegisteredEvent event = new UserRegisteredEvent(Events.newId(), UserRegisteredEvent.TYPE,
                Events.VERSION_1, Instant.now(), userId, "newbie", "Lê Thị Mới", "newbie@test.local", "0901234567");
        String payload = json.write(event);

        kafka.send(Topics.USER_REGISTERED, userId.toString(), payload).get();
        kafka.send(Topics.USER_REGISTERED, userId.toString(), payload).get();
        // a different event for the same user (e.g. identity re-published) must not open a second account
        UserRegisteredEvent again = new UserRegisteredEvent(Events.newId(), UserRegisteredEvent.TYPE,
                Events.VERSION_1, Instant.now(), userId, "newbie", "Lê Thị Mới", "newbie@test.local", null);
        kafka.send(Topics.USER_REGISTERED, userId.toString(), json.write(again)).get();

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM processed_events WHERE event_id IN (?, ?)", Integer.class,
                event.eventId(), again.eventId())).isEqualTo(2));

        assertThat(jdbc.queryForObject("SELECT count(*) FROM customers WHERE user_id = ?", Integer.class, userId))
                .isEqualTo(1);
        Map<String, Object> account = jdbc.queryForMap("""
                SELECT a.id, a.account_number, a.balance, a.status, a.currency, c.full_name
                FROM accounts a JOIN customers c ON c.id = a.customer_id WHERE c.user_id = ?
                """, userId);
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM accounts a JOIN customers c ON c.id = a.customer_id WHERE c.user_id = ?
                """, Integer.class, userId)).isEqualTo(1);
        assertThat(account.get("full_name")).isEqualTo("Lê Thị Mới");
        assertThat(account.get("status")).isEqualTo("ACTIVE");
        assertThat(account.get("currency")).isEqualTo("VND");
        assertThat(Long.parseLong((String) account.get("account_number"))).isGreaterThanOrEqualTo(1000000101L);
        assertThat((java.math.BigDecimal) account.get("balance")).isEqualByComparingTo("0");
        assertThat(jdbc.queryForObject("""
                SELECT per_transaction_limit || '/' || daily_limit FROM transfer_limits WHERE account_id = ?
                """, String.class, account.get("id"))).isEqualTo("100000000.00/500000000.00");
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM outbox_events WHERE aggregate_id = ? AND payload->>'action' = 'ACCOUNT_OPENED'
                """, Integer.class, account.get("id"))).isEqualTo(1);
    }

    private static Optional<ConsumerRecord<String, String>> find(KafkaConsumer<String, String> consumer,
                                                                 String transactionId) {
        for (ConsumerRecord<String, String> record : consumer.poll(Duration.ofMillis(500))) {
            if (record.value().contains(transactionId)) {
                return Optional.of(record);
            }
        }
        return Optional.empty();
    }
}
