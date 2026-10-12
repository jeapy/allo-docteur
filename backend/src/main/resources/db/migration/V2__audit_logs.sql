-- Journal d'audit en ajout seul
CREATE TABLE audit_logs (
    id          UUID PRIMARY KEY,
    actor_id    UUID,
    action      VARCHAR(100) NOT NULL,
    target_type VARCHAR(50),
    target_id   VARCHAR(64),
    ip_address  VARCHAR(64),
    details     TEXT,
    created_at  TIMESTAMPTZ NOT NULL
);
CREATE INDEX ix_audit_logs_created_at ON audit_logs (created_at DESC);
CREATE INDEX ix_audit_logs_actor ON audit_logs (actor_id);
CREATE INDEX ix_audit_logs_target ON audit_logs (target_type, target_id);

CREATE FUNCTION audit_logs_immutable() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'audit_logs est en ajout seul';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_audit_logs_immutable
    BEFORE UPDATE OR DELETE ON audit_logs
    FOR EACH ROW EXECUTE FUNCTION audit_logs_immutable();
