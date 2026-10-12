-- Professionnels de santé, disponibilités, justificatifs

CREATE TABLE professionals (
    user_id                UUID PRIMARY KEY REFERENCES users (id),
    professional_type_id   INTEGER      NOT NULL REFERENCES professional_types (id),
    qualification          VARCHAR(150) NOT NULL,
    license_number         VARCHAR(100) NOT NULL,
    years_experience       INTEGER,
    bio                    TEXT,
    city_id                INTEGER      NOT NULL REFERENCES cities (id),
    district_id            INTEGER REFERENCES districts (id),
    consultation_fee       NUMERIC(12, 2),
    home_visit_fee         NUMERIC(12, 2),
    currency               VARCHAR(3)   NOT NULL DEFAULT 'XOF',
    offers_teleconsultation BOOLEAN     NOT NULL DEFAULT FALSE,
    offers_home_visit      BOOLEAN      NOT NULL DEFAULT FALSE,
    verification_status    VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    reviewed_by            UUID REFERENCES users (id),
    reviewed_at            TIMESTAMPTZ,
    rejection_reason       TEXT,
    rating_avg             NUMERIC(3, 2) NOT NULL DEFAULT 0,
    rating_count           INTEGER      NOT NULL DEFAULT 0,
    created_at             TIMESTAMPTZ  NOT NULL,
    updated_at             TIMESTAMPTZ  NOT NULL,
    created_by             UUID,
    updated_by             UUID,
    version                BIGINT       NOT NULL DEFAULT 0,
    deleted_at             TIMESTAMPTZ,
    CONSTRAINT ck_prof_status CHECK (verification_status IN ('PENDING', 'APPROVED', 'REJECTED', 'SUSPENDED')),
    CONSTRAINT ck_prof_fees CHECK ((consultation_fee IS NULL OR consultation_fee >= 0)
                                   AND (home_visit_fee IS NULL OR home_visit_fee >= 0)),
    CONSTRAINT ck_prof_experience CHECK (years_experience IS NULL OR years_experience BETWEEN 0 AND 70),
    CONSTRAINT ck_prof_rating CHECK (rating_avg BETWEEN 0 AND 5)
);
CREATE UNIQUE INDEX ux_professionals_license ON professionals (lower(license_number)) WHERE deleted_at IS NULL;
-- Index de recherche : seuls les professionnels visibles dans le répertoire
CREATE INDEX ix_prof_search_city ON professionals (city_id, district_id) WHERE verification_status = 'APPROVED' AND deleted_at IS NULL;
CREATE INDEX ix_prof_search_type ON professionals (professional_type_id) WHERE verification_status = 'APPROVED' AND deleted_at IS NULL;
CREATE INDEX ix_prof_search_rating ON professionals (rating_avg DESC, rating_count DESC) WHERE verification_status = 'APPROVED' AND deleted_at IS NULL;
CREATE INDEX ix_prof_status ON professionals (verification_status, created_at);

CREATE TABLE professional_specialties (
    professional_id UUID    NOT NULL REFERENCES professionals (user_id) ON DELETE CASCADE,
    specialty_id    INTEGER NOT NULL REFERENCES specialties (id),
    PRIMARY KEY (professional_id, specialty_id)
);
CREATE INDEX ix_prof_specialties_specialty ON professional_specialties (specialty_id);

CREATE TABLE professional_coverage_zones (
    id              UUID PRIMARY KEY,
    professional_id UUID    NOT NULL REFERENCES professionals (user_id) ON DELETE CASCADE,
    city_id         INTEGER NOT NULL REFERENCES cities (id),
    district_id     INTEGER REFERENCES districts (id)
);
CREATE INDEX ix_coverage_prof ON professional_coverage_zones (professional_id);
CREATE INDEX ix_coverage_area ON professional_coverage_zones (city_id, district_id);

CREATE TABLE professional_documents (
    id                UUID PRIMARY KEY,
    professional_id   UUID         NOT NULL REFERENCES professionals (user_id) ON DELETE CASCADE,
    doc_type          VARCHAR(30)  NOT NULL,
    original_filename VARCHAR(255) NOT NULL,
    storage_key       VARCHAR(255) NOT NULL,
    content_type      VARCHAR(100) NOT NULL,
    size_bytes        BIGINT       NOT NULL,
    checksum_sha256   VARCHAR(64)  NOT NULL,
    uploaded_at       TIMESTAMPTZ  NOT NULL,
    CONSTRAINT ck_prof_doc_type CHECK (doc_type IN ('LICENSE', 'DIPLOMA', 'ID_CARD', 'OTHER'))
);
CREATE INDEX ix_prof_docs_prof ON professional_documents (professional_id);

CREATE TABLE availability_rules (
    id              UUID PRIMARY KEY,
    professional_id UUID    NOT NULL REFERENCES professionals (user_id) ON DELETE CASCADE,
    day_of_week     INTEGER NOT NULL,
    start_time      TIME    NOT NULL,
    end_time        TIME    NOT NULL,
    teleconsultation BOOLEAN NOT NULL DEFAULT TRUE,
    home_visit      BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT ck_avail_day CHECK (day_of_week BETWEEN 1 AND 7),
    CONSTRAINT ck_avail_time CHECK (end_time > start_time),
    CONSTRAINT ck_avail_mode CHECK (teleconsultation OR home_visit)
);
CREATE INDEX ix_avail_rules_search ON availability_rules (day_of_week, professional_id);

-- Indisponibilités ponctuelles (congés, absences). start/end nuls = journée entière.
CREATE TABLE availability_exceptions (
    id              UUID PRIMARY KEY,
    professional_id UUID NOT NULL REFERENCES professionals (user_id) ON DELETE CASCADE,
    exception_date  DATE NOT NULL,
    start_time      TIME,
    end_time        TIME,
    reason          VARCHAR(255),
    CONSTRAINT ck_avail_exc_time CHECK (
        (start_time IS NULL AND end_time IS NULL) OR (start_time IS NOT NULL AND end_time IS NOT NULL AND end_time > start_time))
);
CREATE INDEX ix_avail_exc ON availability_exceptions (professional_id, exception_date);
