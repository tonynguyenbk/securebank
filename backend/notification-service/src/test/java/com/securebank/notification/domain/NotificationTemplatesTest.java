package com.securebank.notification.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.math.BigDecimal;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationTemplatesTest {

    @Test
    void transferSentRendersAllParams() {
        var r = NotificationTemplates.render(TemplateCode.TRANSFER_SENT, Map.of(
                "amount", new BigDecimal("1000000.00"), "currency", "VND", "reference", "TX202610080001",
                "accountNumber", "******0001", "counterpartyName", "Tran Thi Binh",
                "counterpartyAccountNumber", "******0002", "balanceAfter", new BigDecimal("24000000.00")));
        assertThat(r.subject()).isEqualTo("Transfer sent");
        assertThat(r.message()).isEqualTo("You sent 1,000,000 VND to Tran Thi Binh (******0002) from account ******0001. "
                + "Reference TX202610080001. Available balance: 24,000,000 VND.");
    }

    @Test
    void transferReceivedRendersAllParams() {
        var r = NotificationTemplates.render(TemplateCode.TRANSFER_RECEIVED, Map.of(
                "amount", new BigDecimal("1500.50"), "currency", "VND", "reference", "TX1",
                "accountNumber", "******0002", "counterpartyName", "Nguyen Van An",
                "balanceAfter", 11000000));
        assertThat(r.subject()).isEqualTo("Money received");
        assertThat(r.message()).isEqualTo("You received 1,500.5 VND from Nguyen Van An into account ******0002. "
                + "Reference TX1. New balance: 11,000,000 VND.");
    }

    @Test
    void rejectedIncludesFailureCode() {
        var r = NotificationTemplates.render(TemplateCode.TRANSFER_REJECTED, Map.of(
                "amount", 5000000, "currency", "VND", "reference", "TX2", "accountNumber", "******0001",
                "counterpartyAccountNumber", "******0002", "failureCode", "INSUFFICIENT_FUNDS"));
        assertThat(r.message()).contains("5,000,000 VND").contains("(INSUFFICIENT_FUNDS)").contains("TX2");
    }

    @Test
    void frozenAndWelcome() {
        assertThat(NotificationTemplates.render(TemplateCode.ACCOUNT_FROZEN, Map.of("accountNumber", "******0001"))
                .message()).startsWith("Your account ******0001 has been frozen.");
        assertThat(NotificationTemplates.render(TemplateCode.ACCOUNT_UNFROZEN, Map.of("accountNumber", "******0001"))
                .subject()).isEqualTo("Account reactivated");
        assertThat(NotificationTemplates.render(TemplateCode.WELCOME, Map.of("fullName", "Nguyen Van An")).message())
                .startsWith("Hello Nguyen Van An, welcome to SecureBank!");
    }

    @ParameterizedTest
    @EnumSource(TemplateCode.class)
    void everyTemplateRendersWithMissingParams(TemplateCode code) {
        var r = NotificationTemplates.render(code, Map.of());
        assertThat(r.subject()).isNotBlank();
        assertThat(r.message()).isNotBlank().doesNotContain("null");
    }
}
