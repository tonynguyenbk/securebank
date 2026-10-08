package com.securebank.notification.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "notifications")
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "recipient_user_id", nullable = false, updatable = false)
    private UUID recipientUserId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private Channel channel;

    @Enumerated(EnumType.STRING)
    @Column(name = "template_code", nullable = false, updatable = false)
    private TemplateCode templateCode;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, updatable = false, columnDefinition = "jsonb")
    private Map<String, Object> params;

    @Column(nullable = false, updatable = false)
    private String subject;

    @Column(nullable = false, updatable = false)
    private String message;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private NotificationStatus status;

    @Column(name = "read_at")
    private Instant readAt;

    @Column(name = "related_transaction_id", updatable = false)
    private UUID relatedTransactionId;

    @Column(name = "source_event_id", nullable = false, updatable = false)
    private UUID sourceEventId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "sent_at")
    private Instant sentAt;

    protected Notification() {
        // JPA
    }

    public Notification(UUID recipientUserId, Channel channel, TemplateCode templateCode, Map<String, Object> params,
                        NotificationTemplates.Rendered rendered, UUID relatedTransactionId, UUID sourceEventId,
                        Instant createdAt) {
        this.recipientUserId = recipientUserId;
        this.channel = channel;
        this.templateCode = templateCode;
        this.params = new LinkedHashMap<>(params);
        this.subject = rendered.subject();
        this.message = rendered.message();
        this.status = NotificationStatus.PENDING;
        this.relatedTransactionId = relatedTransactionId;
        this.sourceEventId = sourceEventId;
        this.createdAt = createdAt;
    }

    public void markSent(Instant at) {
        this.status = NotificationStatus.SENT;
        this.sentAt = at;
    }

    public void markFailed() {
        this.status = NotificationStatus.FAILED;
    }

    /** Idempotent: re-reading keeps the first read time. */
    public void markRead(Instant at) {
        if (readAt == null) {
            readAt = at;
        }
    }

    public boolean isRead() {
        return readAt != null;
    }

    public UUID getId() {
        return id;
    }

    public UUID getRecipientUserId() {
        return recipientUserId;
    }

    public Channel getChannel() {
        return channel;
    }

    public TemplateCode getTemplateCode() {
        return templateCode;
    }

    public Map<String, Object> getParams() {
        return params;
    }

    public String getSubject() {
        return subject;
    }

    public String getMessage() {
        return message;
    }

    public NotificationStatus getStatus() {
        return status;
    }

    public Instant getReadAt() {
        return readAt;
    }

    public UUID getRelatedTransactionId() {
        return relatedTransactionId;
    }

    public UUID getSourceEventId() {
        return sourceEventId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getSentAt() {
        return sentAt;
    }
}
