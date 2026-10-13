-- Notifications, avis, réclamations, partenaires et polices d'assurance (phase 7)

CREATE TABLE notifications (
    id         UUID PRIMARY KEY,
    user_id    UUID          NOT NULL REFERENCES users (id),
    type       VARCHAR(25)   NOT NULL,
    title      VARCHAR(200)  NOT NULL,
    body       VARCHAR(1000) NOT NULL,
    ref_type   VARCHAR(30),
    ref_id     UUID,
    read_at    TIMESTAMPTZ,
    created_at TIMESTAMPTZ   NOT NULL,
    CONSTRAINT ck_notification_type CHECK (type IN ('APPOINTMENT', 'APPOINTMENT_REMINDER', 'PAYMENT', 'PRESCRIPTION',
        'SOS', 'PHARMACY', 'SUBSCRIPTION', 'REVIEW', 'COMPLAINT_UPDATE', 'PROFESSIONAL', 'SYSTEM'))
);
CREATE INDEX ix_notifications_user ON notifications (user_id, created_at DESC);
CREATE INDEX ix_notifications_unread ON notifications (user_id) WHERE read_at IS NULL;

-- Un rappel par rendez-vous, au plus : l'insertion sert de verrou d'idempotence pour la tâche périodique.
CREATE TABLE appointment_reminders (
    appointment_id UUID PRIMARY KEY REFERENCES appointments (id),
    sent_at        TIMESTAMPTZ NOT NULL
);

CREATE TABLE reviews (
    id              UUID PRIMARY KEY,
    appointment_id  UUID         NOT NULL UNIQUE REFERENCES appointments (id),
    professional_id UUID         NOT NULL REFERENCES professionals (user_id),
    account_id      UUID         NOT NULL REFERENCES users (id),
    author_name     VARCHAR(120) NOT NULL, -- « Prénom N. » : jamais le nom complet
    rating          SMALLINT     NOT NULL,
    comment         VARCHAR(2000),
    status          VARCHAR(10)  NOT NULL DEFAULT 'VISIBLE',
    hidden_reason   VARCHAR(500),
    hidden_by       UUID REFERENCES users (id),
    hidden_at       TIMESTAMPTZ,
    created_at      TIMESTAMPTZ  NOT NULL,
    updated_at      TIMESTAMPTZ  NOT NULL,
    created_by      UUID,
    updated_by      UUID,
    version         BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT ck_review_rating CHECK (rating BETWEEN 1 AND 5),
    CONSTRAINT ck_review_status CHECK (status IN ('VISIBLE', 'HIDDEN')),
    CONSTRAINT ck_review_hidden CHECK ((status = 'HIDDEN') = (hidden_at IS NOT NULL))
);
CREATE INDEX ix_reviews_professional ON reviews (professional_id, created_at DESC) WHERE status = 'VISIBLE';
CREATE INDEX ix_reviews_account ON reviews (account_id, created_at DESC);

CREATE SEQUENCE complaint_number_seq START 1;

CREATE TABLE complaints (
    id             UUID PRIMARY KEY,
    number         VARCHAR(20)  NOT NULL UNIQUE,
    author_id      UUID         NOT NULL REFERENCES users (id),
    author_role    VARCHAR(12)  NOT NULL,
    appointment_id UUID REFERENCES appointments (id),
    subject        VARCHAR(255) NOT NULL,
    description    TEXT         NOT NULL, -- chiffré
    status         VARCHAR(10)  NOT NULL DEFAULT 'OPEN',
    assigned_to    UUID REFERENCES users (id),
    resolution     TEXT,                  -- chiffré
    resolved_at    TIMESTAMPTZ,
    created_at     TIMESTAMPTZ  NOT NULL,
    updated_at     TIMESTAMPTZ  NOT NULL,
    created_by     UUID,
    updated_by     UUID,
    version        BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT ck_complaint_status CHECK (status IN ('OPEN', 'IN_REVIEW', 'RESOLVED', 'REJECTED')),
    CONSTRAINT ck_complaint_role CHECK (author_role IN ('PATIENT', 'PROFESSIONAL')),
    CONSTRAINT ck_complaint_resolved CHECK ((status IN ('RESOLVED', 'REJECTED')) = (resolved_at IS NOT NULL))
);
CREATE INDEX ix_complaints_author ON complaints (author_id, created_at DESC);
CREATE INDEX ix_complaints_status ON complaints (status, created_at);

CREATE TABLE complaint_messages (
    id           UUID PRIMARY KEY,
    complaint_id UUID        NOT NULL REFERENCES complaints (id),
    author_id    UUID        NOT NULL REFERENCES users (id),
    from_admin   BOOLEAN     NOT NULL,
    body         TEXT        NOT NULL, -- chiffré
    created_at   TIMESTAMPTZ NOT NULL
);
CREATE INDEX ix_complaint_messages ON complaint_messages (complaint_id, created_at);

CREATE TABLE partners (
    id                  UUID PRIMARY KEY,
    name                VARCHAR(150) NOT NULL,
    type                VARCHAR(15)  NOT NULL,
    city_id             INTEGER REFERENCES cities (id),
    address             VARCHAR(255),
    contact_email       VARCHAR(255),
    contact_phone       VARCHAR(30),
    contract_start_date DATE,
    status              VARCHAR(10)  NOT NULL DEFAULT 'ACTIVE',
    created_at          TIMESTAMPTZ  NOT NULL,
    updated_at          TIMESTAMPTZ  NOT NULL,
    created_by          UUID,
    updated_by          UUID,
    version             BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT ck_partner_type CHECK (type IN ('CLINIC', 'HEALTH_CENTER', 'HOSPITAL', 'PHARMACY', 'LAB', 'AMBULANCE',
                                               'INSURANCE', 'MUTUAL')),
    CONSTRAINT ck_partner_status CHECK (status IN ('ACTIVE', 'INACTIVE'))
);
CREATE INDEX ix_partners_type ON partners (type) WHERE status = 'ACTIVE';

CREATE TABLE insurance_policies (
    id            UUID PRIMARY KEY,
    account_id    UUID          NOT NULL REFERENCES users (id),
    dependent_id  UUID REFERENCES dependents (id),
    partner_id    UUID          NOT NULL REFERENCES partners (id),
    policy_number TEXT          NOT NULL, -- chiffré
    coverage_rate NUMERIC(5, 2) NOT NULL,
    valid_until   DATE          NOT NULL,
    created_at    TIMESTAMPTZ   NOT NULL,
    updated_at    TIMESTAMPTZ   NOT NULL,
    created_by    UUID,
    updated_by    UUID,
    version       BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT ck_policy_rate CHECK (coverage_rate BETWEEN 0 AND 100)
);
CREATE INDEX ix_policies_account ON insurance_policies (account_id);
