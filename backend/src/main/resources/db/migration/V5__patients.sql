-- Profil patient et personnes à charge (gérées par le compte parent)

CREATE TABLE patients (
    user_id                 UUID PRIMARY KEY REFERENCES users (id),
    birth_date              DATE,
    gender                  VARCHAR(10),
    city_id                 INTEGER REFERENCES cities (id),
    district_id             INTEGER REFERENCES districts (id),
    address                 VARCHAR(255),
    emergency_contact_name  VARCHAR(150),
    emergency_contact_phone VARCHAR(30),
    premium                 BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at              TIMESTAMPTZ NOT NULL,
    updated_at              TIMESTAMPTZ NOT NULL,
    created_by              UUID,
    updated_by              UUID,
    version                 BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT ck_patient_gender CHECK (gender IS NULL OR gender IN ('MALE', 'FEMALE', 'OTHER'))
);

CREATE TABLE dependents (
    id           UUID PRIMARY KEY,
    guardian_id  UUID         NOT NULL REFERENCES users (id),
    first_name   VARCHAR(100) NOT NULL,
    last_name    VARCHAR(100) NOT NULL,
    birth_date   DATE         NOT NULL,
    gender       VARCHAR(10),
    relationship VARCHAR(20)  NOT NULL,
    created_at   TIMESTAMPTZ  NOT NULL,
    updated_at   TIMESTAMPTZ  NOT NULL,
    created_by   UUID,
    updated_by   UUID,
    version      BIGINT       NOT NULL DEFAULT 0,
    deleted_at   TIMESTAMPTZ,
    CONSTRAINT ck_dependent_gender CHECK (gender IS NULL OR gender IN ('MALE', 'FEMALE', 'OTHER')),
    CONSTRAINT ck_dependent_relationship CHECK (relationship IN ('CHILD', 'SPOUSE', 'PARENT', 'OTHER'))
);
CREATE INDEX ix_dependents_guardian ON dependents (guardian_id) WHERE deleted_at IS NULL;
