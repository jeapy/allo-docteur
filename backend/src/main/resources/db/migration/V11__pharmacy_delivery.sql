-- Pharmacie numérique : pharmacies partenaires, commandes d'ordonnance, livraison

-- Les pharmaciens sont des professionnels (validés par l'administration comme les autres)
INSERT INTO professional_types (code, label_fr) VALUES ('PHARMACIST', 'Pharmacien(ne)');

CREATE SEQUENCE pharmacy_order_number_seq START WITH 1 INCREMENT BY 1;

CREATE TABLE pharmacies (
    id             UUID PRIMARY KEY,
    name           VARCHAR(200)   NOT NULL,
    city_id        INTEGER        NOT NULL REFERENCES cities (id),
    district_id    INTEGER REFERENCES districts (id),
    address        VARCHAR(255)   NOT NULL,
    phone          VARCHAR(30)    NOT NULL,
    latitude       NUMERIC(9, 6),
    longitude      NUMERIC(9, 6),
    opening_hours  VARCHAR(255),
    on_duty        BOOLEAN        NOT NULL DEFAULT FALSE,
    offers_delivery BOOLEAN       NOT NULL DEFAULT FALSE,
    delivery_fee   NUMERIC(12, 2) NOT NULL DEFAULT 0,
    currency       VARCHAR(3)     NOT NULL DEFAULT 'XOF',
    active         BOOLEAN        NOT NULL DEFAULT TRUE,
    created_at     TIMESTAMPTZ    NOT NULL,
    updated_at     TIMESTAMPTZ    NOT NULL,
    created_by     UUID,
    updated_by     UUID,
    version        BIGINT         NOT NULL DEFAULT 0,
    CONSTRAINT ck_pharmacy_geo CHECK ((latitude IS NULL) = (longitude IS NULL)),
    CONSTRAINT ck_pharmacy_lat CHECK (latitude IS NULL OR latitude BETWEEN -90 AND 90),
    CONSTRAINT ck_pharmacy_lon CHECK (longitude IS NULL OR longitude BETWEEN -180 AND 180),
    CONSTRAINT ck_pharmacy_fee CHECK (delivery_fee >= 0)
);
CREATE INDEX ix_pharmacies_city ON pharmacies (city_id, district_id) WHERE active;
CREATE INDEX ix_pharmacies_duty ON pharmacies (city_id) WHERE active AND on_duty;

-- Pharmaciens habilités à traiter les commandes d'une pharmacie (rattachement par l'administration)
CREATE TABLE pharmacy_members (
    pharmacy_id UUID        NOT NULL REFERENCES pharmacies (id),
    user_id     UUID        NOT NULL REFERENCES professionals (user_id),
    added_at    TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (pharmacy_id, user_id)
);
CREATE INDEX ix_pharmacy_members_user ON pharmacy_members (user_id);

CREATE TABLE pharmacy_orders (
    id                 UUID PRIMARY KEY,
    number             VARCHAR(30)    NOT NULL UNIQUE,
    prescription_id    UUID           NOT NULL REFERENCES prescriptions (id),
    record_id          UUID           NOT NULL REFERENCES medical_records (id),
    account_id         UUID           NOT NULL REFERENCES users (id),
    dependent_id       UUID REFERENCES dependents (id),
    beneficiary_name   VARCHAR(200)   NOT NULL,
    pharmacy_id        UUID           NOT NULL REFERENCES pharmacies (id),
    fulfillment        VARCHAR(10)    NOT NULL,
    status             VARCHAR(20)    NOT NULL,
    contact_phone      VARCHAR(30)    NOT NULL,
    delivery_address   VARCHAR(255),
    delivery_latitude  NUMERIC(9, 6),
    delivery_longitude NUMERIC(9, 6),
    patient_note       TEXT,
    pharmacy_message   VARCHAR(500),
    quoted_total       NUMERIC(12, 2),
    delivery_fee       NUMERIC(12, 2) NOT NULL DEFAULT 0,
    currency           VARCHAR(3)     NOT NULL DEFAULT 'XOF',
    accepted_at        TIMESTAMPTZ,
    ready_at           TIMESTAMPTZ,
    completed_at       TIMESTAMPTZ,
    cancelled_at       TIMESTAMPTZ,
    created_at         TIMESTAMPTZ    NOT NULL,
    updated_at         TIMESTAMPTZ    NOT NULL,
    created_by         UUID,
    updated_by         UUID,
    version            BIGINT         NOT NULL DEFAULT 0,
    CONSTRAINT ck_order_fulfillment CHECK (fulfillment IN ('PICKUP', 'DELIVERY')),
    CONSTRAINT ck_order_status CHECK (status IN ('SENT', 'ACCEPTED', 'READY', 'OUT_FOR_DELIVERY', 'COMPLETED',
        'DELIVERED', 'DELIVERY_FAILED', 'REJECTED', 'CANCELLED')),
    CONSTRAINT ck_order_delivery_address CHECK (fulfillment <> 'DELIVERY' OR delivery_address IS NOT NULL),
    CONSTRAINT ck_order_geo CHECK ((delivery_latitude IS NULL) = (delivery_longitude IS NULL)),
    CONSTRAINT ck_order_amounts CHECK (delivery_fee >= 0 AND (quoted_total IS NULL OR quoted_total >= 0))
);
-- une ordonnance n'alimente qu'une commande en cours à la fois
CREATE UNIQUE INDEX ux_order_active_prescription ON pharmacy_orders (prescription_id)
    WHERE status IN ('SENT', 'ACCEPTED', 'READY', 'OUT_FOR_DELIVERY');
CREATE INDEX ix_orders_account ON pharmacy_orders (account_id, created_at DESC);
CREATE INDEX ix_orders_pharmacy ON pharmacy_orders (pharmacy_id, status, created_at DESC);

-- Copie des lignes de l'ordonnance au moment de l'envoi : la pharmacie ne lit jamais le dossier du patient.
CREATE TABLE pharmacy_order_items (
    id              UUID PRIMARY KEY,
    order_id        UUID    NOT NULL REFERENCES pharmacy_orders (id) ON DELETE CASCADE,
    line_number     INTEGER NOT NULL,
    medication_name TEXT    NOT NULL,
    dosage          TEXT,
    frequency       TEXT,
    quantity        INTEGER,
    instructions    TEXT,
    available       BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT ux_order_line UNIQUE (order_id, line_number)
);

CREATE TABLE deliveries (
    id                UUID PRIMARY KEY,
    order_id          UUID         NOT NULL UNIQUE REFERENCES pharmacy_orders (id),
    status            VARCHAR(15)  NOT NULL,
    provider          VARCHAR(40)  NOT NULL,
    tracking_code     VARCHAR(60)  NOT NULL,
    courier_name      VARCHAR(120),
    courier_phone     VARCHAR(30),
    handover_code     TEXT         NOT NULL,
    code_attempts     INTEGER      NOT NULL DEFAULT 0,
    dispatched_at     TIMESTAMPTZ  NOT NULL,
    delivered_at      TIMESTAMPTZ,
    failure_reason    VARCHAR(500),
    created_at        TIMESTAMPTZ  NOT NULL,
    updated_at        TIMESTAMPTZ  NOT NULL,
    created_by        UUID,
    updated_by        UUID,
    version           BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT ck_delivery_status CHECK (status IN ('IN_TRANSIT', 'DELIVERED', 'FAILED')),
    CONSTRAINT ck_delivery_attempts CHECK (code_attempts >= 0)
);
