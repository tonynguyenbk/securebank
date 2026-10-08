-- Idempotency (spec §12). The UNIQUE (user_id, idempotency_key) index is the serialization point for
-- duplicate requests: the transfer transaction claims the key first, so a concurrent duplicate blocks on
-- the index until the first transaction commits (then it replays the stored response) or rolls back.
-- A row only becomes visible to other transactions together with its stored response.
CREATE TABLE idempotency_records (
    id               UUID PRIMARY KEY,
    user_id          UUID         NOT NULL,
    idempotency_key  VARCHAR(100) NOT NULL,
    request_hash     VARCHAR(128) NOT NULL,
    transaction_id   UUID REFERENCES bank_transactions (id),
    response_code    INTEGER,
    response_body    TEXT,
    created_at       TIMESTAMPTZ  NOT NULL,
    completed_at     TIMESTAMPTZ,
    expires_at       TIMESTAMPTZ  NOT NULL,
    CONSTRAINT uq_idempotency_user_key UNIQUE (user_id, idempotency_key)
);
