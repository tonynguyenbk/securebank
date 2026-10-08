-- One row per transfer attempt that reached the business rules: SUCCESS (money moved, two ledger lines)
-- or REJECTED (a business rule refused it, no ledger lines). The UUID is the internal identifier; the
-- transaction_reference (TXyyyyMMddNNNN) is the human-readable one shown to customers and staff.
CREATE TABLE bank_transactions (
    id                          UUID PRIMARY KEY,
    transaction_reference       VARCHAR(50)    NOT NULL,
    transaction_type            VARCHAR(30)    NOT NULL,
    source_account_id           UUID           NOT NULL REFERENCES accounts (id),
    destination_account_id      UUID REFERENCES accounts (id),
    destination_account_number  VARCHAR(30)    NOT NULL,
    amount                      NUMERIC(19, 2) NOT NULL,
    currency                    VARCHAR(3)     NOT NULL,
    description                 VARCHAR(255),
    status                      VARCHAR(20)    NOT NULL,
    failure_code                VARCHAR(50),
    failure_reason              VARCHAR(255),
    created_by                  UUID           NOT NULL,
    created_at                  TIMESTAMPTZ    NOT NULL,
    completed_at                TIMESTAMPTZ,
    CONSTRAINT uq_bank_transactions_reference UNIQUE (transaction_reference),
    CONSTRAINT ck_bank_transactions_amount_positive CHECK (amount > 0),
    CONSTRAINT ck_bank_transactions_status CHECK (status IN ('PENDING', 'SUCCESS', 'FAILED', 'REJECTED')),
    CONSTRAINT ck_bank_transactions_type CHECK (transaction_type IN ('INTERNAL_TRANSFER')),
    CONSTRAINT ck_bank_transactions_distinct_accounts CHECK (destination_account_id IS NULL
                                                             OR destination_account_id <> source_account_id),
    CONSTRAINT ck_bank_transactions_failure CHECK (
        (status = 'SUCCESS' AND failure_code IS NULL AND completed_at IS NOT NULL)
        OR status <> 'SUCCESS')
);
