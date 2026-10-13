-- Ordonnances électroniques

CREATE SEQUENCE prescription_number_seq START WITH 1 INCREMENT BY 1;

CREATE TABLE prescriptions (
    id               UUID PRIMARY KEY,
    number           VARCHAR(30)  NOT NULL UNIQUE,
    appointment_id   UUID         NOT NULL REFERENCES appointments (id),
    record_id        UUID         NOT NULL REFERENCES medical_records (id),
    professional_id  UUID         NOT NULL REFERENCES professionals (user_id),
    account_id       UUID         NOT NULL REFERENCES users (id),
    dependent_id     UUID REFERENCES dependents (id),
    beneficiary_name VARCHAR(200) NOT NULL,
    issued_at        TIMESTAMPTZ  NOT NULL,
    valid_until      TIMESTAMPTZ  NOT NULL,
    status           VARCHAR(15)  NOT NULL,
    cancelled_at     TIMESTAMPTZ,
    cancel_reason    VARCHAR(500),
    notes            TEXT,
    integrity_hash   VARCHAR(64)  NOT NULL,
    created_at       TIMESTAMPTZ  NOT NULL,
    updated_at       TIMESTAMPTZ  NOT NULL,
    created_by       UUID,
    updated_by       UUID,
    version          BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT ck_prescription_status CHECK (status IN ('ISSUED', 'CANCELLED')),
    CONSTRAINT ck_prescription_validity CHECK (valid_until > issued_at),
    CONSTRAINT ck_prescription_cancel CHECK ((status = 'CANCELLED') = (cancelled_at IS NOT NULL))
);
CREATE INDEX ix_prescriptions_account ON prescriptions (account_id, issued_at DESC);
CREATE INDEX ix_prescriptions_record ON prescriptions (record_id, issued_at DESC);
CREATE INDEX ix_prescriptions_professional ON prescriptions (professional_id, issued_at DESC);
CREATE INDEX ix_prescriptions_appointment ON prescriptions (appointment_id);

-- Médicament, posologie et consignes chiffrés au repos par l'application.
CREATE TABLE prescription_items (
    id              UUID PRIMARY KEY,
    prescription_id UUID    NOT NULL REFERENCES prescriptions (id) ON DELETE CASCADE,
    line_number     INTEGER NOT NULL,
    medication_name TEXT    NOT NULL,
    dosage          TEXT,
    frequency       TEXT,
    duration_days   INTEGER,
    quantity        INTEGER,
    instructions    TEXT,
    CONSTRAINT ux_prescription_line UNIQUE (prescription_id, line_number),
    CONSTRAINT ck_item_duration CHECK (duration_days IS NULL OR duration_days BETWEEN 1 AND 365),
    CONSTRAINT ck_item_quantity CHECK (quantity IS NULL OR quantity BETWEEN 1 AND 1000)
);
