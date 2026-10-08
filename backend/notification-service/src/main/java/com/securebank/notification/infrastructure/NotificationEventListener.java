package com.securebank.notification.infrastructure;

import com.securebank.common.events.AccountStatusChangedEvent;
import com.securebank.common.events.DomainEvent;
import com.securebank.common.events.Topics;
import com.securebank.common.events.TransactionCompletedEvent;
import com.securebank.common.events.TransactionFailedEvent;
import com.securebank.common.events.UserRegisteredEvent;
import com.securebank.common.json.EventJson;
import com.securebank.notification.application.NotificationPlanner;
import com.securebank.notification.application.NotificationService;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/** Kafka entry points (api.md §6). Fraud alerts are intentionally not consumed (no tipping-off). */
@Component
public class NotificationEventListener {

    private final EventJson eventJson;
    private final NotificationService service;

    public NotificationEventListener(EventJson eventJson, NotificationService service) {
        this.eventJson = eventJson;
        this.service = service;
    }

    @KafkaListener(topics = Topics.TRANSACTION_COMPLETED)
    public void onTransactionCompleted(String payload) {
        TransactionCompletedEvent e = parse(payload, TransactionCompletedEvent.class);
        service.notify(e, NotificationPlanner.plan(e));
    }

    @KafkaListener(topics = Topics.TRANSACTION_FAILED)
    public void onTransactionFailed(String payload) {
        TransactionFailedEvent e = parse(payload, TransactionFailedEvent.class);
        service.notify(e, NotificationPlanner.plan(e));
    }

    @KafkaListener(topics = Topics.ACCOUNT_STATUS_CHANGED)
    public void onAccountStatusChanged(String payload) {
        AccountStatusChangedEvent e = parse(payload, AccountStatusChangedEvent.class);
        service.notify(e, NotificationPlanner.plan(e));
    }

    @KafkaListener(topics = Topics.USER_REGISTERED)
    public void onUserRegistered(String payload) {
        UserRegisteredEvent e = parse(payload, UserRegisteredEvent.class);
        service.notify(e, NotificationPlanner.plan(e));
    }

    private <T extends DomainEvent> T parse(String payload, Class<T> type) {
        T event;
        try {
            event = eventJson.read(payload, type);
        } catch (IllegalArgumentException e) {
            throw new MalformedEventException("Unreadable " + type.getSimpleName(), e);
        }
        if (event == null || event.eventId() == null) {
            throw new MalformedEventException(type.getSimpleName() + " has no eventId", null);
        }
        return event;
    }
}
