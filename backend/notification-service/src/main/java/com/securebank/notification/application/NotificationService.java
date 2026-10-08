package com.securebank.notification.application;

import com.securebank.common.events.DomainEvent;
import com.securebank.common.kafka.ProcessedEventStore;
import com.securebank.notification.domain.Channel;
import com.securebank.notification.domain.Notification;
import com.securebank.notification.domain.NotificationTemplates;
import com.securebank.notification.repository.NotificationRepository;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;

/** Creates and (simulated-)delivers notifications for one consumed event, exactly once per eventId. */
@Service
public class NotificationService {

    public static final String CONSUMER_NAME = "notification-service";

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final ProcessedEventStore processedEvents;
    private final NotificationRepository notifications;
    private final NotificationSender sender;
    private final MeterRegistry meters;
    private final Clock clock;

    public NotificationService(ProcessedEventStore processedEvents, NotificationRepository notifications,
                               NotificationSender sender, MeterRegistry meters, Clock clock) {
        this.processedEvents = processedEvents;
        this.notifications = notifications;
        this.sender = sender;
        this.meters = meters;
        this.clock = clock;
    }

    /** @return number of notifications created (0 for a duplicate delivery) */
    @Transactional
    public int notify(DomainEvent event, List<NotificationPlanner.Planned> plans) {
        if (!processedEvents.markIfFirst(event.eventId(), CONSUMER_NAME)) {
            log.info("Duplicate {} eventId={} ignored", event.eventType(), event.eventId());
            return 0;
        }
        int created = 0;
        for (NotificationPlanner.Planned plan : plans) {
            NotificationTemplates.Rendered rendered = NotificationTemplates.render(plan.template(), plan.params());
            List<Channel> channels = plan.channels().stream().sorted(Comparator.naturalOrder()).toList();
            for (Channel channel : channels) {
                Instant now = clock.instant();
                Notification n = notifications.save(new Notification(plan.recipientUserId(), channel, plan.template(),
                        plan.params(), rendered, plan.relatedTransactionId(), event.eventId(), now));
                deliver(n);
                created++;
            }
        }
        return created;
    }

    private void deliver(Notification n) {
        if (n.getChannel() == Channel.IN_APP) {
            n.markSent(clock.instant()); // stored = delivered: the app reads it from here
        } else {
            try {
                sender.send(n);
                n.markSent(clock.instant());
            } catch (RuntimeException e) {
                n.markFailed();
                log.warn("Delivery failed notificationId={} channel={}: {}", n.getId(), n.getChannel(), e.toString());
            }
        }
        meters.counter("notification_total", "channel", n.getChannel().name(), "status", n.getStatus().name())
                .increment();
    }
}
