-- Indexes for the access paths used by the service (spec §40). Unique constraints already index
-- customers.user_id, accounts.account_number, bank_transactions.transaction_reference,
-- transfer_limits.account_id and idempotency_records (user_id, idempotency_key).

CREATE INDEX ix_customers_created_at ON customers (created_at);

CREATE INDEX ix_accounts_customer_id ON accounts (customer_id);
CREATE INDEX ix_accounts_status ON accounts (status);

-- daily-limit sum: WHERE source_account_id = ? AND status = 'SUCCESS' AND created_at >= ?
CREATE INDEX ix_bank_tx_source_status_created ON bank_transactions (source_account_id, status, created_at);
CREATE INDEX ix_bank_tx_destination_created ON bank_transactions (destination_account_id, created_at);
CREATE INDEX ix_bank_tx_status_created ON bank_transactions (status, created_at);
CREATE INDEX ix_bank_tx_created_at ON bank_transactions (created_at);

-- statement (passbook) per account, newest first
CREATE INDEX ix_ledger_account_created ON ledger_entries (account_id, created_at);
CREATE INDEX ix_ledger_transaction ON ledger_entries (transaction_id);

CREATE INDEX ix_idempotency_expires_at ON idempotency_records (expires_at);
CREATE INDEX ix_idempotency_transaction ON idempotency_records (transaction_id);
