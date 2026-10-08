-- Outgoing transfer limits per account (spec §14). The daily limit applies to the sum of today's SUCCESS
-- outgoing transfers, "today" being the Asia/Ho_Chi_Minh calendar day.
CREATE TABLE transfer_limits (
    id                     UUID PRIMARY KEY,
    account_id             UUID           NOT NULL REFERENCES accounts (id),
    per_transaction_limit  NUMERIC(19, 2) NOT NULL,
    daily_limit            NUMERIC(19, 2) NOT NULL,
    updated_at             TIMESTAMPTZ    NOT NULL,
    updated_by             UUID,
    CONSTRAINT uq_transfer_limits_account UNIQUE (account_id),
    CONSTRAINT ck_transfer_limits_positive CHECK (per_transaction_limit > 0 AND daily_limit > 0),
    CONSTRAINT ck_transfer_limits_order CHECK (per_transaction_limit <= daily_limit)
);
