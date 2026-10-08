package com.securebank.notification.application;

import com.securebank.common.error.ApiException;
import com.securebank.common.error.ErrorCode;
import com.securebank.common.web.PageResponse;
import com.securebank.notification.application.dto.NotificationView;
import com.securebank.notification.application.dto.UnreadCount;
import com.securebank.notification.domain.Channel;
import com.securebank.notification.domain.Notification;
import com.securebank.notification.repository.NotificationRepository;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** The caller's own notifications. The recipient is always the JWT subject, never a request parameter. */
@Service
public class NotificationQueryService {

    private final NotificationRepository notifications;
    private final Clock clock;

    public NotificationQueryService(NotificationRepository notifications, Clock clock) {
        this.notifications = notifications;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public PageResponse<NotificationView> mine(UUID userId, Channel channel, boolean unreadOnly, Pageable pageable) {
        Specification<Notification> spec = (root, query, cb) -> {
            List<Predicate> p = new ArrayList<>(3);
            p.add(cb.equal(root.get("recipientUserId"), userId));
            if (channel != null) {
                p.add(cb.equal(root.get("channel"), channel));
            }
            if (unreadOnly) {
                p.add(cb.isNull(root.get("readAt")));
            }
            return cb.and(p.toArray(Predicate[]::new));
        };
        return PageResponse.of(notifications.findAll(spec, pageable), NotificationView::from);
    }

    @Transactional(readOnly = true)
    public UnreadCount unreadCount(UUID userId) {
        return new UnreadCount(notifications.countByRecipientUserIdAndChannelAndReadAtIsNull(userId, Channel.IN_APP));
    }

    /** Someone else's notification is reported as not found, so ids cannot be probed. */
    @Transactional
    public void markRead(UUID notificationId, UUID userId) {
        Notification n = notifications.findByIdAndRecipientUserId(notificationId, userId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOTIFICATION_NOT_FOUND));
        n.markRead(clock.instant());
    }
}
