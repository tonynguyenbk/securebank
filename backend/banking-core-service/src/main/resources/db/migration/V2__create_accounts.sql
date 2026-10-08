-- Accounts. Money is NUMERIC(19,2) (never floating point); the database itself refuses a negative balance,
-- so even a bug in the application cannot overdraw an account.
CREATE SEQUENCE account_number_seq START WITH 1000000101 INCREMENT BY 1 NO CYCLE;
-- Demo accounts use the reserved range 1000000001..1000000100 (docs/contracts/api.md §7).

CREATE TABLE accounts (
    id              UUID PRIMARY KEY,
    customer_id     UUID           NOT NULL REFERENCES customers (id),
    account_number  VARCHAR(30)    NOT NULL,
    account_type    VARCHAR(20)    NOT NULL DEFAULT 'CURRENT',
    currency        VARCHAR(3)     NOT NULL,
    balance         NUMERIC(19, 2) NOT NULL,
    status          VARCHAR(20)    NOT NULL,
    version         BIGINT         NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ    NOT NULL,
    updated_at      TIMESTAMPTZ    NOT NULL,
    CONSTRAINT uq_accounts_account_number UNIQUE (account_number),
    CONSTRAINT ck_accounts_balance_non_negative CHECK (balance >= 0),
    CONSTRAINT ck_accounts_status CHECK (status IN ('ACTIVE', 'FROZEN', 'CLOSED')),
    CONSTRAINT ck_accounts_type CHECK (account_type IN ('CURRENT')),
    CONSTRAINT ck_accounts_currency CHECK (currency ~ '^[A-Z]{3}$')
);
