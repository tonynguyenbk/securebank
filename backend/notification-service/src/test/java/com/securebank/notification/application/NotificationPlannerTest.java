package com.securebank.notification.application;

import com.securebank.common.events.AccountStatusChangedEvent;
import com.securebank.common.events.Events;
import com.securebank.common.events.TransactionCompletedEvent;
import com.securebank.common.events.UserRegisteredEvent;
import com.securebank.notification.domain.Channel;
import com.securebank.notification.domain.TemplateCode;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationPlannerTest {

    private final UUID sender = UUID.randomUUID();
    private final UUID recipient = UUID.randomUUID();

    @Test
    void completedTransferNotifiesBothPartiesWithMaskedAccounts() {
        var e = new TransactionCompletedEvent(Events.newId(), TransactionCompletedEvent.TYPE, 1, Instant.now(),
                UUID.randomUUID(), "TX1", UUID.randomUUID(), "1000000001", UUID.randomUUID(), "1000000002",
                UUID.randomUUID(), "Nguyen Van An", sender, UUID.randomUUID(), "Tran Thi Binh", recipient,
                new BigDecimal("1000000.00"), "VND", null, new BigDecimal("24000000.00"), new BigDecimal("11000000.00"));

        List<NotificationPlanner.Planned> plans = NotificationPlanner.plan(e);

        assertThat(plans).hasSize(2);
        var sent = plans.get(0);
        assertThat(sent.recipientUserId()).isEqualTo(sender);
        assertThat(sent.template()).isEqualTo(TemplateCode.TRANSFER_SENT);
        assertThat(sent.channels()).isEqualTo(Set.of(Channel.IN_APP, Channel.EMAIL));
        assertThat(sent.params()).containsEntry("accountNumber", "******0001")
                .containsEntry("counterpartyAccountNumber", "******0002")
                .containsEntry("counterpartyName", "Tran Thi Binh")
                .containsEntry("balanceAfter", new BigDecimal("24000000.00"))
                .doesNotContainKey("description");
        var received = plans.get(1);
        assertThat(received.recipientUserId()).isEqualTo(recipient);
        assertThat(received.template()).isEqualTo(TemplateCode.TRANSFER_RECEIVED);
        assertThat(received.channels()).isEqualTo(Set.of(Channel.IN_APP, Channel.SMS));
        assertThat(received.params()).containsEntry("accountNumber", "******0002")
                .containsEntry("counterpartyName", "Nguyen Van An")
                .containsEntry("balanceAfter", new BigDecimal("11000000.00"));
        assertThat(received.relatedTransactionId()).isEqualTo(e.transactionId());
    }

    @Test
    void accountStatusChangesMapToTemplates() {
        assertThat(NotificationPlanner.plan(status("ACTIVE", "FROZEN")).getFirst().template())
                .isEqualTo(TemplateCode.ACCOUNT_FROZEN);
        assertThat(NotificationPlanner.plan(status("FROZEN", "ACTIVE")).getFirst().template())
                .isEqualTo(TemplateCode.ACCOUNT_UNFROZEN);
        assertThat(NotificationPlanner.plan(status("ACTIVE", "CLOSED"))).isEmpty();
        // the freeze reason may mention fraud: it is never passed to the customer
        assertThat(NotificationPlanner.plan(status("ACTIVE", "FROZEN")).getFirst().params()).doesNotContainKey("reason");
    }

    @Test
    void welcomeIsInAppOnly() {
        var plans = NotificationPlanner.plan(new UserRegisteredEvent(Events.newId(), UserRegisteredEvent.TYPE, 1,
                Instant.now(), sender, "newuser", "New User", "new@example.com", null));
        assertThat(plans).singleElement().satisfies(p -> {
            assertThat(p.template()).isEqualTo(TemplateCode.WELCOME);
            assertThat(p.channels()).containsExactly(Channel.IN_APP);
        });
    }

    private AccountStatusChangedEvent status(String from, String to) {
        return new AccountStatusChangedEvent(Events.newId(), AccountStatusChangedEvent.TYPE, 1, Instant.now(),
                UUID.randomUUID(), "1000000001", UUID.randomUUID(), sender, from, to, "Suspected fraud TX1",
                UUID.randomUUID());
    }
}
