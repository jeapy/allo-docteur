-- Rendez-vous : téléconsultation (vidéo, audio, chat) et visite à domicile

CREATE EXTENSION IF NOT EXISTS btree_gist;

CREATE TABLE appointments (
    id                     UUID PRIMARY KEY,
    patient_id             UUID          NOT NULL REFERENCES users (id),
    dependent_id           UUID REFERENCES dependents (id),
    beneficiary_name       VARCHAR(200)  NOT NULL,
    beneficiary_birth_date DATE,
    professional_id        UUID          NOT NULL REFERENCES professionals (user_id),
    type                   VARCHAR(30)   NOT NULL,
    reason                 VARCHAR(40)   NOT NULL,
    reason_details         TEXT,
    start_at               TIMESTAMPTZ   NOT NULL,
    end_at                 TIMESTAMPTZ   NOT NULL,
    status                 VARCHAR(20)   NOT NULL,
    address                VARCHAR(255),
    latitude               NUMERIC(9, 6),
    longitude              NUMERIC(9, 6),
    fee                    NUMERIC(12, 2) NOT NULL DEFAULT 0,
    currency               VARCHAR(3)    NOT NULL DEFAULT 'XOF',
    confirmed_at           TIMESTAMPTZ,
    started_at             TIMESTAMPTZ,
    completed_at           TIMESTAMPTZ,
    cancelled_at           TIMESTAMPTZ,
    cancelled_by           UUID REFERENCES users (id),
    cancel_reason          VARCHAR(1000),
    created_at             TIMESTAMPTZ   NOT NULL,
    updated_at             TIMESTAMPTZ   NOT NULL,
    created_by             UUID,
    updated_by             UUID,
    version                BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT ck_appt_type CHECK (type IN ('TELECONSULTATION_VIDEO', 'TELECONSULTATION_AUDIO', 'CHAT', 'HOME_VISIT')),
    CONSTRAINT ck_appt_status CHECK (status IN ('REQUESTED', 'CONFIRMED', 'IN_PROGRESS', 'COMPLETED', 'CANCELLED', 'NO_SHOW')),
    CONSTRAINT ck_appt_reason CHECK (reason IN ('CONSULTATION', 'TREATMENT_FOLLOWUP', 'MEDICAL_CERTIFICATE', 'INJECTION',
        'DRESSING', 'INFUSION', 'BLOOD_PRESSURE', 'MEDICAL_SURVEILLANCE', 'PREGNANCY_FOLLOWUP', 'POSTNATAL',
        'BREASTFEEDING_ADVICE', 'ELDERLY_ASSISTANCE', 'HYGIENE_CARE', 'DAILY_COMPANIONSHIP')),
    CONSTRAINT ck_appt_period CHECK (end_at > start_at),
    CONSTRAINT ck_appt_fee CHECK (fee >= 0),
    CONSTRAINT ck_appt_home_address CHECK (type <> 'HOME_VISIT' OR address IS NOT NULL),
    CONSTRAINT ck_appt_geo CHECK ((latitude IS NULL) = (longitude IS NULL)),
    -- Garantie ultime contre la double réservation (la vérification applicative n'est qu'un confort)
    CONSTRAINT ex_appt_no_overlap EXCLUDE USING gist (
        professional_id WITH =,
        tstzrange(start_at, end_at) WITH &&
    ) WHERE (status IN ('REQUESTED', 'CONFIRMED', 'IN_PROGRESS'))
);
CREATE INDEX ix_appt_patient ON appointments (patient_id, start_at DESC);
CREATE INDEX ix_appt_professional ON appointments (professional_id, start_at DESC);
CREATE INDEX ix_appt_status_start ON appointments (status, start_at);
CREATE INDEX ix_appt_start ON appointments (start_at);
CREATE INDEX ix_appt_dependent ON appointments (dependent_id) WHERE dependent_id IS NOT NULL;
