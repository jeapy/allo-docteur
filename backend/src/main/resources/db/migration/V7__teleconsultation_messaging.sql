-- Sessions audio/vidéo, messagerie et documents partagés d'un rendez-vous

CREATE TABLE teleconsultation_sessions (
    id             UUID PRIMARY KEY,
    appointment_id UUID         NOT NULL UNIQUE REFERENCES appointments (id),
    provider       VARCHAR(30)  NOT NULL,
    room_id        VARCHAR(255) NOT NULL,
    media          VARCHAR(10)  NOT NULL,
    started_at     TIMESTAMPTZ  NOT NULL,
    ended_at       TIMESTAMPTZ,
    CONSTRAINT ck_tele_media CHECK (media IN ('VIDEO', 'AUDIO')),
    CONSTRAINT ck_tele_period CHECK (ended_at IS NULL OR ended_at >= started_at)
);

-- Contenu chiffré au repos (AttributeConverter) : donnée de santé
CREATE TABLE messages (
    id             UUID PRIMARY KEY,
    appointment_id UUID        NOT NULL REFERENCES appointments (id),
    sender_id      UUID        NOT NULL REFERENCES users (id),
    content        TEXT        NOT NULL,
    sent_at        TIMESTAMPTZ NOT NULL,
    read_at        TIMESTAMPTZ
);
CREATE INDEX ix_messages_appointment ON messages (appointment_id, sent_at);
CREATE INDEX ix_messages_unread ON messages (appointment_id, sender_id) WHERE read_at IS NULL;

CREATE TABLE shared_documents (
    id                UUID PRIMARY KEY,
    appointment_id    UUID         NOT NULL REFERENCES appointments (id),
    uploaded_by       UUID         NOT NULL REFERENCES users (id),
    kind              VARCHAR(10)  NOT NULL,
    original_filename VARCHAR(255) NOT NULL,
    caption           VARCHAR(255),
    storage_key       VARCHAR(255) NOT NULL,
    content_type      VARCHAR(100) NOT NULL,
    size_bytes        BIGINT       NOT NULL,
    checksum_sha256   VARCHAR(64)  NOT NULL,
    uploaded_at       TIMESTAMPTZ  NOT NULL,
    CONSTRAINT ck_shared_doc_kind CHECK (kind IN ('IMAGE', 'PDF'))
);
CREATE INDEX ix_shared_docs_appointment ON shared_documents (appointment_id, uploaded_at);
