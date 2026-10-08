package com.securebank.notification.application.dto;

import com.securebank.notification.domain.Channel;
import com.securebank.notification.domain.Notification;
import com.securebank.notification.domain.NotificationStatus;
import com.securebank.notification.domain.TemplateCode;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/** {@code Notification} of api.md §6. */
public record NotificationView(
        UUID id,
        Channel channel,
        NotificationStatus status,
        TemplateCode templateCode,
        Map<String, Object> params,
        String subject,
        String message,
        boolean read,
        UUID relatedTransactionId,
        Instant createdAt,
        Instant sentAt
) {
    public static NotificationView from(Notification n) {
        return new NotificationView(n.getId(), n.getChannel(), n.getStatus(), n.getTemplateCode(), n.getParams(),
                n.getSubject(), n.getMessage(), n.isRead(), n.getRelatedTransactionId(), n.getCreatedAt(),
                n.getSentAt());
    }
}
