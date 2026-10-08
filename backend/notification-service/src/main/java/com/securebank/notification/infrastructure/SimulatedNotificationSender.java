package com.securebank.notification.infrastructure;

import com.securebank.notification.application.NotificationSender;
import com.securebank.notification.domain.Notification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Simulated EMAIL/SMS provider: logs a one-line delivery record instead of calling a paid provider.
 * The log line is deliberately PII-safe: recipient user id, template, generic subject and the already-masked
 * account number only — never names, the message body, amounts or balances.
 */
@Component
public class SimulatedNotificationSender implements NotificationSender {

    private static final Logger log = LoggerFactory.getLogger(SimulatedNotificationSender.class);

    @Override
    public void send(Notification n) {
        Object account = n.getParams().getOrDefault("accountNumber", "-");
        log.info("[SIMULATED {}] notificationId={} recipientUserId={} template={} subject=\"{}\" account={}",
                n.getChannel(), n.getId(), n.getRecipientUserId(), n.getTemplateCode(), n.getSubject(), account);
    }
}
