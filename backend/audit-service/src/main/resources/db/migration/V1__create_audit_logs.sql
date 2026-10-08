-- Append-only audit trail (spec §20, api.md §5).

CREATE TABLE audit_logs (
    id              UUID PRIMARY KEY,
    event_id        UUID          NOT NULL,
    occurred_at     TIMESTAMPTZ   NOT NULL,
    received_at     TIMESTAMPTZ   NOT NULL,
    actor_user_id   UUID,
    actor_username  VARCHAR(100),
    actor_role      VARCHAR(30),
    action          VARCHAR(60)   NOT NULL,
    resource_type   VARCHAR(40)   NOT NULL,
    resource_id     VARCHAR(100),
    before          JSONB,
    after           JSONB,
    outcome         VARCHAR(10)   NOT NULL,
    correlation_id  VARCHAR(64),
    ip_address      VARCHAR(64),
    source_service  VARCHAR(60)   NOT NULL,
    -- consumer idempotency: a redelivered event hits this constraint and is ignored (ON CONFLICT DO NOTHING)
    CONSTRAINT uq_audit_logs_event UNIQUE (event_id),
    CONSTRAINT ck_audit_logs_outcome CHECK (outcome IN ('SUCCESS', 'FAILURE'))
);

CREATE INDEX ix_audit_logs_occurred_at ON audit_logs (occurred_at DESC);
CREATE INDEX ix_audit_logs_actor ON audit_logs (actor_user_id);
CREATE INDEX ix_audit_logs_action ON audit_logs (action);
CREATE INDEX ix_audit_logs_resource ON audit_logs (resource_type, resource_id);
CREATE INDEX ix_audit_logs_correlation ON audit_logs (correlation_id);

-- Append-only at the database level: no UPDATE, DELETE or TRUNCATE, whatever the application does.
CREATE FUNCTION audit_logs_reject_mutation() RETURNS trigger
    LANGUAGE plpgsql AS
$$
BEGIN
    RAISE EXCEPTION 'audit_logs is append-only: % is not allowed', TG_OP
        USING ERRCODE = 'restrict_violation';
END;
$$;

CREATE TRIGGER trg_audit_logs_no_update_delete
    BEFORE UPDATE OR DELETE ON audit_logs
    FOR EACH ROW EXECUTE FUNCTION audit_logs_reject_mutation();

CREATE TRIGGER trg_audit_logs_no_truncate
    BEFORE TRUNCATE ON audit_logs
    FOR EACH STATEMENT EXECUTE FUNCTION audit_logs_reject_mutation();
