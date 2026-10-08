package com.securebank.notification.application;

import com.securebank.common.events.AccountStatusChangedEvent;
import com.securebank.common.events.TransactionCompletedEvent;
import com.securebank.common.events.TransactionFailedEvent;
import com.securebank.common.events.UserRegisteredEvent;
import com.securebank.common.web.Masking;
import com.securebank.notification.domain.Channel;
import com.securebank.notification.domain.TemplateCode;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Decides who is notified about an event, through which channels and with which parameters (api.md §6).
 * Pure function of the event. Account numbers in params are always masked. Fraud alerts are never notified
 * to customers (no tipping-off), and freeze reasons are not passed on for the same reason.
 */
public final class NotificationPlanner {

    private NotificationPlanner() {
    }

    public record Planned(UUID recipientUserId, TemplateCode template, Set<Channel> channels,
                          Map<String, Object> params, UUID relatedTransactionId) {
    }

    public static List<Planned> plan(TransactionCompletedEvent e) {
        List<Planned> plans = new ArrayList<>(2);
        if (e.sourceUserId() != null) {
            plans.add(new Planned(e.sourceUserId(), TemplateCode.TRANSFER_SENT, Set.of(Channel.IN_APP, Channel.EMAIL),
                    params("amount", e.amount(), "currency", e.currency(), "reference", e.transactionReference(),
                            "accountNumber", Masking.accountNumber(e.sourceAccountNumber()),
                            "counterpartyName", e.destinationCustomerName(),
                            "counterpartyAccountNumber", Masking.accountNumber(e.destinationAccountNumber()),
                            "balanceAfter", e.sourceBalanceAfter()),
                    e.transactionId()));
        }
        if (e.destinationUserId() != null) {
            plans.add(new Planned(e.destinationUserId(), TemplateCode.TRANSFER_RECEIVED,
                    Set.of(Channel.IN_APP, Channel.SMS),
                    params("amount", e.amount(), "currency", e.currency(), "reference", e.transactionReference(),
                            "accountNumber", Masking.accountNumber(e.destinationAccountNumber()),
                            "counterpartyName", e.customerName(),
                            "counterpartyAccountNumber", Masking.accountNumber(e.sourceAccountNumber()),
                            "balanceAfter", e.destinationBalanceAfter()),
                    e.transactionId()));
        }
        return plans;
    }

    public static List<Planned> plan(TransactionFailedEvent e) {
        if (e.sourceUserId() == null) {
            return List.of();
        }
        return List.of(new Planned(e.sourceUserId(), TemplateCode.TRANSFER_REJECTED, Set.of(Channel.IN_APP),
                params("amount", e.amount(), "currency", e.currency(), "reference", e.transactionReference(),
                        "accountNumber", Masking.accountNumber(e.sourceAccountNumber()),
                        "counterpartyAccountNumber", Masking.accountNumber(e.destinationAccountNumber()),
                        "failureCode", e.failureCode()),
                e.transactionId()));
    }

    public static List<Planned> plan(AccountStatusChangedEvent e) {
        if (e.userId() == null) {
            return List.of();
        }
        TemplateCode template;
        if ("FROZEN".equals(e.newStatus())) {
            template = TemplateCode.ACCOUNT_FROZEN;
        } else if ("ACTIVE".equals(e.newStatus()) && "FROZEN".equals(e.previousStatus())) {
            template = TemplateCode.ACCOUNT_UNFROZEN;
        } else {
            return List.of(); // e.g. CLOSED: no template in v1
        }
        return List.of(new Planned(e.userId(), template, Set.of(Channel.IN_APP, Channel.EMAIL),
                params("accountNumber", Masking.accountNumber(e.accountNumber()), "status", e.newStatus()), null));
    }

    public static List<Planned> plan(UserRegisteredEvent e) {
        if (e.userId() == null) {
            return List.of();
        }
        return List.of(new Planned(e.userId(), TemplateCode.WELCOME, Set.of(Channel.IN_APP),
                params("fullName", e.fullName(), "username", e.username()), null));
    }

    /** Ordered map that skips null values (params are {@code Record<string, string | number>}). */
    private static Map<String, Object> params(Object... keyValues) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            if (keyValues[i + 1] != null) {
                map.put((String) keyValues[i], keyValues[i + 1]);
            }
        }
        return map;
    }
}
