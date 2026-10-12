-- Utilisateurs et authentification (Phase 1)
CREATE TABLE users (
    id                    UUID PRIMARY KEY,
    email                 VARCHAR(255) NOT NULL,
    phone                 VARCHAR(20),
    password_hash         VARCHAR(100) NOT NULL,
    role                  VARCHAR(20)  NOT NULL,
    admin_level           VARCHAR(20),
    first_name            VARCHAR(100),
    last_name             VARCHAR(100),
    photo_url             VARCHAR(500),
    status                VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    two_factor_enabled    BOOLEAN      NOT NULL DEFAULT FALSE,
    two_factor_secret     TEXT,
    failed_login_attempts INTEGER      NOT NULL DEFAULT 0,
    locked_until          TIMESTAMPTZ,
    last_login_at         TIMESTAMPTZ,
    created_at            TIMESTAMPTZ  NOT NULL,
    updated_at            TIMESTAMPTZ  NOT NULL,
    created_by            UUID,
    updated_by            UUID,
    version               BIGINT       NOT NULL DEFAULT 0,
    deleted_at            TIMESTAMPTZ,
    CONSTRAINT ck_users_role   CHECK (role IN ('PATIENT', 'PROFESSIONAL', 'ADMIN')),
    CONSTRAINT ck_users_status CHECK (status IN ('ACTIVE', 'SUSPENDED', 'PENDING')),
    CONSTRAINT ck_users_admin_level CHECK (
        (role = 'ADMIN' AND admin_level IN ('SUPER', 'SUPPORT', 'FINANCE'))
        OR (role <> 'ADMIN' AND admin_level IS NULL))
);

-- Unicité de l'e-mail (insensible à la casse) et du téléphone parmi les comptes non supprimés
CREATE UNIQUE INDEX ux_users_email ON users (lower(email)) WHERE deleted_at IS NULL;
CREATE UNIQUE INDEX ux_users_phone ON users (phone) WHERE deleted_at IS NULL AND phone IS NOT NULL;
CREATE INDEX ix_users_role_status ON users (role, status);
CREATE INDEX ix_users_created_at ON users (created_at DESC);

CREATE TABLE refresh_tokens (
    id         UUID PRIMARY KEY,
    user_id    UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    family_id  UUID        NOT NULL,
    token_hash VARCHAR(64) NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    revoked_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL
);
CREATE UNIQUE INDEX ux_refresh_tokens_hash ON refresh_tokens (token_hash);
CREATE INDEX ix_refresh_tokens_user ON refresh_tokens (user_id);
CREATE INDEX ix_refresh_tokens_family ON refresh_tokens (family_id);

CREATE TABLE password_reset_tokens (
    id         UUID PRIMARY KEY,
    user_id    UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    token_hash VARCHAR(64) NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    used_at    TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL
);
CREATE UNIQUE INDEX ux_password_reset_tokens_hash ON password_reset_tokens (token_hash);
CREATE INDEX ix_password_reset_tokens_user ON password_reset_tokens (user_id);

CREATE TABLE two_factor_recovery_codes (
    id         UUID PRIMARY KEY,
    user_id    UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    code_hash  VARCHAR(64) NOT NULL,
    used_at    TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX ix_recovery_codes_user ON two_factor_recovery_codes (user_id);
