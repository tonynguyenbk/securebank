-- Simulated customer notifications (spec §21, api.md §6).

CREATE TABLE notifications (
    id                      UUID PRIMARY KEY,
    recipient_user_id       UUID          NOT NULL,
    channel                 VARCHAR(10)   NOT NULL,
    template_code           VARCHAR(40)   NOT NULL,
    params                  JSONB         NOT NULL,
    subject                 VARCHAR(200)  NOT NULL,
    message                 VARCHAR(1000) NOT NULL,
    status                  VARCHAR(10)   NOT NULL,
    read_at                 TIMESTAMPTZ,
    related_transaction_id  UUID,
    source_event_id         UUID          NOT NULL,
    created_at              TIMESTAMPTZ   NOT NULL,
    sent_at                 TIMESTAMPTZ,
    -- Backstop for consumer idempotency (processed_events is the primary guard). template_code is part of the key
    -- because a transfer between two accounts of the same user legitimately yields TRANSFER_SENT and
    -- TRANSFER_RECEIVED in-app notifications for the same (event, recipient, channel).
    CONSTRAINT uq_notifications_source UNIQUE (source_event_id, recipient_user_id, channel, template_code),
    CONSTRAINT ck_notifications_channel CHECK (channel IN ('EMAIL', 'SMS', 'IN_APP')),
    CONSTRAINT ck_notifications_status CHECK (status IN ('PENDING', 'SENT', 'FAILED'))
);

CREATE INDEX ix_notifications_recipient ON notifications (recipient_user_id, created_at DESC);
CREATE INDEX ix_notifications_created_at ON notifications (created_at DESC);
CREATE INDEX ix_notifications_unread ON notifications (recipient_user_id) WHERE read_at IS NULL AND channel = 'IN_APP';
