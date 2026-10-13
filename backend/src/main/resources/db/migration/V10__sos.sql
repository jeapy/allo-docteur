-- SOS : structures de santé, alertes géolocalisées, accès d'urgence au dossier

-- Saisies par l'administration (aucune donnée pré-remplie : coordonnées et téléphones doivent être vérifiés).
CREATE TABLE health_facilities (
    id                UUID PRIMARY KEY,
    name              VARCHAR(200)  NOT NULL,
    type              VARCHAR(20)   NOT NULL,
    city_id           INTEGER       NOT NULL REFERENCES cities (id),
    district_id       INTEGER REFERENCES districts (id),
    address           VARCHAR(255),
    phone             VARCHAR(30),
    latitude          NUMERIC(9, 6) NOT NULL,
    longitude         NUMERIC(9, 6) NOT NULL,
    emergency_service BOOLEAN       NOT NULL DEFAULT TRUE,
    active            BOOLEAN       NOT NULL DEFAULT TRUE,
    created_at        TIMESTAMPTZ   NOT NULL,
    updated_at        TIMESTAMPTZ   NOT NULL,
    created_by        UUID,
    updated_by        UUID,
    version           BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT ck_facility_type CHECK (type IN ('HOSPITAL', 'CLINIC', 'HEALTH_CENTER')),
    CONSTRAINT ck_facility_lat CHECK (latitude BETWEEN -90 AND 90),
    CONSTRAINT ck_facility_lon CHECK (longitude BETWEEN -180 AND 180)
);
CREATE INDEX ix_facilities_city ON health_facilities (city_id) WHERE active;

CREATE TABLE sos_alerts (
    id                      UUID PRIMARY KEY,
    account_id              UUID          NOT NULL REFERENCES users (id),
    dependent_id            UUID REFERENCES dependents (id),
    beneficiary_name        VARCHAR(200)  NOT NULL,
    beneficiary_birth_date  DATE,
    category                VARCHAR(20)   NOT NULL,
    status                  VARCHAR(15)   NOT NULL,
    latitude                NUMERIC(9, 6) NOT NULL,
    longitude               NUMERIC(9, 6) NOT NULL,
    address                 VARCHAR(255)  NOT NULL,
    city_id                 INTEGER REFERENCES cities (id),
    contact_phone           VARCHAR(30),
    description             TEXT,
    assigned_professional_id UUID REFERENCES professionals (user_id),
    appointment_id          UUID REFERENCES appointments (id),
    accepted_at             TIMESTAMPTZ,
    resolved_at             TIMESTAMPTZ,
    resolution_note         TEXT,
    cancelled_at            TIMESTAMPTZ,
    cancelled_by            UUID REFERENCES users (id),
    cancel_reason           VARCHAR(500),
    created_at              TIMESTAMPTZ   NOT NULL,
    updated_at              TIMESTAMPTZ   NOT NULL,
    created_by              UUID,
    updated_by              UUID,
    version                 BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT ck_sos_category CHECK (category IN ('MEDICAL', 'ACCIDENT', 'PREGNANCY', 'CHILD', 'OTHER')),
    CONSTRAINT ck_sos_status CHECK (status IN ('OPEN', 'ACCEPTED', 'RESOLVED', 'CANCELLED')),
    CONSTRAINT ck_sos_lat CHECK (latitude BETWEEN -90 AND 90),
    CONSTRAINT ck_sos_lon CHECK (longitude BETWEEN -180 AND 180),
    CONSTRAINT ck_sos_assignment CHECK ((status IN ('ACCEPTED', 'RESOLVED')) = (assigned_professional_id IS NOT NULL)
                                        OR status = 'CANCELLED')
);
-- une seule alerte active par personne concernée : évite les doublons en cas de pression répétée sur le bouton
CREATE UNIQUE INDEX ux_sos_active_person ON sos_alerts
    (account_id, COALESCE(dependent_id, '00000000-0000-0000-0000-000000000000'::uuid))
    WHERE status IN ('OPEN', 'ACCEPTED');
CREATE INDEX ix_sos_open ON sos_alerts (city_id, created_at DESC) WHERE status = 'OPEN';
CREATE INDEX ix_sos_account ON sos_alerts (account_id, created_at DESC);
CREATE INDEX ix_sos_professional ON sos_alerts (assigned_professional_id, created_at DESC)
    WHERE assigned_professional_id IS NOT NULL;
CREATE INDEX ix_sos_status ON sos_alerts (status, created_at DESC);

-- « Bris de glace » : accès d'urgence au dossier sans consentement, motivé, borné dans le temps, visible du patient.
CREATE TABLE emergency_access_grants (
    id              UUID PRIMARY KEY,
    alert_id        UUID        NOT NULL REFERENCES sos_alerts (id),
    professional_id UUID        NOT NULL REFERENCES professionals (user_id),
    reason          TEXT        NOT NULL,
    granted_at      TIMESTAMPTZ NOT NULL,
    expires_at      TIMESTAMPTZ NOT NULL,
    CONSTRAINT ux_emergency_grant UNIQUE (alert_id, professional_id),
    CONSTRAINT ck_emergency_period CHECK (expires_at > granted_at)
);
CREATE INDEX ix_emergency_professional ON emergency_access_grants (professional_id, expires_at DESC);
