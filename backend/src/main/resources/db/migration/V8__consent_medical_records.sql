-- Consentement numérique, dossier médical, carnet de santé et journal d'accès

-- Textes de consentement versionnés (le plus récent publié est « la version courante »)
CREATE TABLE consent_versions (
    version      VARCHAR(20) PRIMARY KEY,
    title        VARCHAR(200) NOT NULL,
    content      TEXT         NOT NULL,
    published_at TIMESTAMPTZ  NOT NULL
);

INSERT INTO consent_versions (version, title, content, published_at) VALUES (
    '1.0',
    'Consentement au traitement de mes données de santé',
    'En acceptant, j''autorise Allo Docteur à collecter et conserver les données de santé que je saisis ou que mes professionnels de santé ajoutent à mon dossier (et à celui des personnes à ma charge), '
    || 'afin de permettre les consultations, les visites à domicile et le suivi médical.' || E'\n\n'
    || '1. Seuls les professionnels de santé avec lesquels j''ai un rendez-vous confirmé, en cours ou passé peuvent consulter mon dossier.' || E'\n'
    || '2. Chaque consultation de mon dossier est enregistrée et je peux voir à tout moment qui y a accédé.' || E'\n'
    || '3. Je peux retirer mon consentement à tout moment ; les professionnels perdent alors immédiatement l''accès à mon dossier.' || E'\n'
    || '4. Mes données sont chiffrées au repos et ne sont pas accessibles à l''équipe d''administration de la plateforme.' || E'\n\n'
    || 'TEXTE PROVISOIRE : à faire valider par un conseil juridique avant mise en production.',
    TIMESTAMPTZ '2026-01-01 00:00:00+00'
);

CREATE TABLE consents (
    id         UUID PRIMARY KEY,
    user_id    UUID        NOT NULL REFERENCES users (id),
    version    VARCHAR(20) NOT NULL REFERENCES consent_versions (version),
    granted_at TIMESTAMPTZ NOT NULL,
    revoked_at TIMESTAMPTZ,
    ip_address VARCHAR(64),
    CONSTRAINT ck_consent_period CHECK (revoked_at IS NULL OR revoked_at >= granted_at)
);
-- un seul consentement actif par compte
CREATE UNIQUE INDEX ux_consents_active ON consents (user_id) WHERE revoked_at IS NULL;
CREATE INDEX ix_consents_user ON consents (user_id, granted_at DESC);

-- Un dossier par personne : le titulaire du compte ou l'une de ses personnes à charge.
-- Champs de santé chiffrés au repos par l'application (AttributeConverter).
CREATE TABLE medical_records (
    id                  UUID PRIMARY KEY,
    account_id          UUID        NOT NULL REFERENCES users (id),
    dependent_id        UUID REFERENCES dependents (id),
    blood_group         VARCHAR(10),
    allergies           TEXT,
    chronic_conditions  TEXT,
    current_medications TEXT,
    family_history      TEXT,
    created_at          TIMESTAMPTZ NOT NULL,
    updated_at          TIMESTAMPTZ NOT NULL,
    created_by          UUID,
    updated_by          UUID,
    version             BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT ck_record_blood CHECK (blood_group IS NULL
        OR blood_group IN ('A_POS', 'A_NEG', 'B_POS', 'B_NEG', 'AB_POS', 'AB_NEG', 'O_POS', 'O_NEG'))
);
CREATE UNIQUE INDEX ux_record_self ON medical_records (account_id) WHERE dependent_id IS NULL;
CREATE UNIQUE INDEX ux_record_dependent ON medical_records (dependent_id) WHERE dependent_id IS NOT NULL;

-- Entrées en ajout seul côté clinique ; l'auteur patient peut retirer les siennes (suppression logique).
CREATE TABLE medical_record_entries (
    id             UUID PRIMARY KEY,
    record_id      UUID        NOT NULL REFERENCES medical_records (id),
    appointment_id UUID REFERENCES appointments (id),
    author_id      UUID        NOT NULL REFERENCES users (id),
    author_role    VARCHAR(20) NOT NULL,
    kind           VARCHAR(30) NOT NULL,
    title          TEXT        NOT NULL,
    content        TEXT,
    occurred_on    DATE        NOT NULL,
    created_at     TIMESTAMPTZ NOT NULL,
    deleted_at     TIMESTAMPTZ,
    CONSTRAINT ck_entry_author_role CHECK (author_role IN ('PATIENT', 'PROFESSIONAL')),
    CONSTRAINT ck_entry_kind CHECK (kind IN ('CONSULTATION_NOTE', 'DIAGNOSIS', 'VACCINATION', 'MEASUREMENT',
                                             'LAB_RESULT', 'HOSPITALIZATION', 'PERSONAL_NOTE'))
);
CREATE INDEX ix_entries_record ON medical_record_entries (record_id, occurred_on DESC, created_at DESC)
    WHERE deleted_at IS NULL;

-- Qui a consulté ou modifié quoi, et quand. Les refus sont journalisés aussi.
CREATE TABLE medical_access_logs (
    id             UUID PRIMARY KEY,
    record_id      UUID        NOT NULL REFERENCES medical_records (id),
    actor_id       UUID        NOT NULL REFERENCES users (id),
    actor_role     VARCHAR(20) NOT NULL,
    action         VARCHAR(40) NOT NULL,
    target_id      UUID,
    outcome        VARCHAR(10) NOT NULL,
    reason         VARCHAR(100),
    ip_address     VARCHAR(64),
    occurred_at    TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_access_outcome CHECK (outcome IN ('GRANTED', 'DENIED'))
);
CREATE INDEX ix_access_logs_record ON medical_access_logs (record_id, occurred_at DESC);
CREATE INDEX ix_access_logs_actor ON medical_access_logs (actor_id, occurred_at DESC);
CREATE INDEX ix_access_logs_time ON medical_access_logs (occurred_at DESC);

CREATE FUNCTION medical_access_logs_immutable() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'medical_access_logs est en ajout seul';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_medical_access_logs_immutable
    BEFORE UPDATE OR DELETE ON medical_access_logs
    FOR EACH ROW EXECUTE FUNCTION medical_access_logs_immutable();
