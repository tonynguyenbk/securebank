-- Identity: users and roles (api.md §1). Passwords are BCrypt hashes, never plaintext.
CREATE TABLE users (
    id            UUID PRIMARY KEY,
    username      VARCHAR(50)  NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    full_name     VARCHAR(200) NOT NULL,
    email         VARCHAR(254) NOT NULL,
    phone         VARCHAR(20),
    enabled       BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMPTZ  NOT NULL,
    updated_at    TIMESTAMPTZ  NOT NULL,
    version       BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT uq_users_username UNIQUE (username)
);

CREATE INDEX ix_users_email ON users (email);
CREATE INDEX ix_users_created_at ON users (created_at);

CREATE TABLE roles (
    id   SMALLINT PRIMARY KEY,
    name VARCHAR(30) NOT NULL,
    CONSTRAINT uq_roles_name UNIQUE (name)
);

INSERT INTO roles (id, name) VALUES
    (1, 'CUSTOMER'),
    (2, 'BANK_STAFF'),
    (3, 'AUDITOR'),
    (4, 'ADMIN');

CREATE TABLE user_roles (
    user_id UUID     NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    role_id SMALLINT NOT NULL REFERENCES roles (id),
    PRIMARY KEY (user_id, role_id)
);

CREATE INDEX ix_user_roles_role ON user_roles (role_id);
