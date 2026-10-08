-- Per-day counter behind readable references TX + yyyyMMdd (Asia/Ho_Chi_Minh) + NNNN (spec §31).
-- Incremented with INSERT ... ON CONFLICT DO UPDATE ... RETURNING inside the business transaction:
-- the row lock makes concurrent increments serialize, and uq_bank_transactions_reference backs it up.
CREATE TABLE transaction_reference_counters (
    business_date  DATE   PRIMARY KEY,
    last_value     BIGINT NOT NULL,
    CONSTRAINT ck_reference_counter_positive CHECK (last_value > 0)
);
