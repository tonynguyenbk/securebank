-- Opaque refresh tokens (api.md §1). Only the SHA-256 hash (hex) of the token is stored.
-- Rotation: the consumed token gets revoked_at + replaced_by_id pointing at its successor.
CREATE TABLE refresh_tokens (
    id             UUID PRIMARY KEY,
    user_id        UUID         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    token_hash     VARCHAR(64)  NOT NULL,
    expires_at     TIMESTAMPTZ  NOT NULL,
    revoked_at     TIMESTAMPTZ,
    replaced_by_id UUID REFERENCES refresh_tokens (id),
    created_at     TIMESTAMPTZ  NOT NULL,
    created_ip     VARCHAR(64),
    user_agent     VARCHAR(255),
    CONSTRAINT uq_refresh_tokens_token_hash UNIQUE (token_hash)
);

-- "revoke every active token of this user" (logout-all / reuse detection)
CREATE INDEX ix_refresh_tokens_user_active ON refresh_tokens (user_id) WHERE revoked_at IS NULL;
CREATE INDEX ix_refresh_tokens_expires_at ON refresh_tokens (expires_at);
