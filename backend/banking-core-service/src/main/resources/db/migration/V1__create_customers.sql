-- Customer profiles. user_id links to identity-service's users.id (no cross-database FK by design:
-- each service owns its database). UNIQUE(user_id) also makes UserRegistered consumption idempotent.
CREATE TABLE customers (
    id          UUID PRIMARY KEY,
    user_id     UUID         NOT NULL,
    full_name   VARCHAR(200) NOT NULL,
    email       VARCHAR(200) NOT NULL,
    phone       VARCHAR(50),
    created_at  TIMESTAMPTZ  NOT NULL,
    updated_at  TIMESTAMPTZ  NOT NULL,
    CONSTRAINT uq_customers_user_id UNIQUE (user_id)
);
