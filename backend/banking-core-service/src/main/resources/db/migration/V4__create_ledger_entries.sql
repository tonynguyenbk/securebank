-- Double-entry ledger (spec §13). Every SUCCESS transfer has exactly one DEBIT (source) and one CREDIT
-- (destination) line of the same amount. The table is append-only: the application has no update/delete
-- code path, and the triggers below make the database itself reject UPDATE, DELETE and TRUNCATE.
CREATE TABLE ledger_entries (
    id              UUID PRIMARY KEY,
    transaction_id  UUID           NOT NULL REFERENCES bank_transactions (id),
    account_id      UUID           NOT NULL REFERENCES accounts (id),
    entry_type      VARCHAR(10)    NOT NULL,
    amount          NUMERIC(19, 2) NOT NULL,
    balance_before  NUMERIC(19, 2) NOT NULL,
    balance_after   NUMERIC(19, 2) NOT NULL,
    created_at      TIMESTAMPTZ    NOT NULL,
    -- at most one DEBIT and one CREDIT per transaction
    CONSTRAINT uq_ledger_entries_tx_type UNIQUE (transaction_id, entry_type),
    CONSTRAINT ck_ledger_entries_type CHECK (entry_type IN ('DEBIT', 'CREDIT')),
    CONSTRAINT ck_ledger_entries_amount_positive CHECK (amount > 0),
    CONSTRAINT ck_ledger_entries_balance_non_negative CHECK (balance_before >= 0 AND balance_after >= 0),
    -- the running balance must be arithmetically consistent with the movement
    CONSTRAINT ck_ledger_entries_arithmetic CHECK (
        (entry_type = 'DEBIT' AND balance_after = balance_before - amount)
        OR (entry_type = 'CREDIT' AND balance_after = balance_before + amount))
);

CREATE FUNCTION ledger_entries_append_only() RETURNS trigger
    LANGUAGE plpgsql AS
$$
BEGIN
    RAISE EXCEPTION 'ledger_entries is append-only: % is not allowed', TG_OP
        USING ERRCODE = 'restrict_violation';
END;
$$;

CREATE TRIGGER trg_ledger_entries_no_update_delete
    BEFORE UPDATE OR DELETE ON ledger_entries
    FOR EACH ROW EXECUTE FUNCTION ledger_entries_append_only();

CREATE TRIGGER trg_ledger_entries_no_truncate
    BEFORE TRUNCATE ON ledger_entries
    FOR EACH STATEMENT EXECUTE FUNCTION ledger_entries_append_only();
