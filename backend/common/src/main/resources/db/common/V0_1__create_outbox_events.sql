-- Transactional outbox (spec §18). Shared by every producing service:
-- spring.flyway.locations: classpath:db/migration,classpath:db/common
CREATE TABLE outbox_events (
    id              UUID PRIMARY KEY,               -- equals the event's eventId
    aggregate_type  VARCHAR(100) NOT NULL,
    aggregate_id    UUID,
    event_type      VARCHAR(100) NOT NULL,
    topic           VARCHAR(200) NOT NULL,
    event_key       VARCHAR(100),
    payload         JSONB        NOT NULL,
    correlation_id  VARCHAR(64),
    status          VARCHAR(20)  NOT NULL DEFAULT 'NEW',
    retry_count     INTEGER      NOT NULL DEFAULT 0,
    last_error      VARCHAR(500),
    created_at      TIMESTAMPTZ  NOT NULL,
    next_attempt_at TIMESTAMPTZ  NOT NULL,
    published_at    TIMESTAMPTZ,
    CONSTRAINT ck_outbox_status CHECK (status IN ('NEW', 'PUBLISHED', 'FAILED'))
);

CREATE INDEX ix_outbox_pending ON outbox_events (status, next_attempt_at) WHERE status <> 'PUBLISHED';
CREATE INDEX ix_outbox_aggregate ON outbox_events (aggregate_type, aggregate_id);
