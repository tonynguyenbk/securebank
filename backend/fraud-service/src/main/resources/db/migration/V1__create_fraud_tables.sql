-- Fraud detection (spec §15, api.md §4).

CREATE TABLE fraud_alerts (
    id                          UUID PRIMARY KEY,
    transaction_id              UUID          NOT NULL,
    transaction_reference       VARCHAR(40)   NOT NULL,
    customer_id                 UUID          NOT NULL,
    customer_name               VARCHAR(200)  NOT NULL,
    user_id                     UUID,
    source_account_id           UUID          NOT NULL,
    source_account_number       VARCHAR(20)   NOT NULL,
    destination_account_number  VARCHAR(20)   NOT NULL,
    destination_customer_name   VARCHAR(200),
    amount                      NUMERIC(19, 2) NOT NULL,
    currency                    VARCHAR(3)    NOT NULL,
    transaction_occurred_at     TIMESTAMPTZ   NOT NULL,
    risk_score                  INTEGER       NOT NULL,
    risk_level                  VARCHAR(20)   NOT NULL,
    status                      VARCHAR(20)   NOT NULL,
    created_at                  TIMESTAMPTZ   NOT NULL,
    reviewed_by                 UUID,
    reviewed_by_username        VARCHAR(100),
    reviewed_at                 TIMESTAMPTZ,
    review_note                 VARCHAR(1000),
    version                     BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT uq_fraud_alerts_transaction UNIQUE (transaction_id),
    CONSTRAINT ck_fraud_alerts_status CHECK (status IN ('OPEN', 'UNDER_REVIEW', 'APPROVED', 'REJECTED', 'CLOSED')),
    CONSTRAINT ck_fraud_alerts_level CHECK (risk_level IN ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL')),
    CONSTRAINT ck_fraud_alerts_score CHECK (risk_score >= 0)
);

CREATE INDEX ix_fraud_alerts_status ON fraud_alerts (status);
CREATE INDEX ix_fraud_alerts_risk_level ON fraud_alerts (risk_level);
CREATE INDEX ix_fraud_alerts_created_at ON fraud_alerts (created_at DESC);
CREATE INDEX ix_fraud_alerts_customer ON fraud_alerts (customer_id);

CREATE TABLE fraud_rule_hits (
    id                  UUID PRIMARY KEY,
    alert_id            UUID          NOT NULL REFERENCES fraud_alerts (id),
    rule_code           VARCHAR(40)   NOT NULL,
    description         VARCHAR(255)  NOT NULL,
    score_contribution  INTEGER       NOT NULL,
    details             VARCHAR(500)  NOT NULL,
    CONSTRAINT uq_fraud_rule_hits UNIQUE (alert_id, rule_code)
);

CREATE TABLE fraud_alert_status_history (
    id              UUID PRIMARY KEY,
    alert_id        UUID          NOT NULL REFERENCES fraud_alerts (id),
    status          VARCHAR(20)   NOT NULL,
    actor_user_id   UUID,
    actor_username  VARCHAR(100),
    note            VARCHAR(1000),
    changed_at      TIMESTAMPTZ   NOT NULL
);

CREATE INDEX ix_fraud_alert_history_alert ON fraud_alert_status_history (alert_id, changed_at);

-- Source of truth for the NEW_BENEFICIARY rule; Redis holds a cache that can be rebuilt from here.
CREATE TABLE known_beneficiaries (
    customer_id             UUID        NOT NULL,
    destination_account_id  UUID        NOT NULL,
    first_seen_at           TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (customer_id, destination_account_id)
);
