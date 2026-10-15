-- Vérification du numéro d'ordre professionnel
--   UNCHECKED    jamais examiné
--   FORMAT_VALID format conforme, aucun registre officiel consulté
--   CONFIRMED    confirmé (registre officiel ou vérification manuelle par l'administration)
--   NOT_FOUND    introuvable auprès de l'ordre : l'approbation est bloquée
ALTER TABLE professionals
    ADD COLUMN license_check_status VARCHAR(15) NOT NULL DEFAULT 'UNCHECKED',
    ADD COLUMN license_checked_at   TIMESTAMPTZ,
    ADD COLUMN license_checked_by   UUID REFERENCES users (id),
    ADD COLUMN license_check_note   VARCHAR(500),
    ADD CONSTRAINT ck_prof_license_check CHECK (license_check_status IN ('UNCHECKED', 'FORMAT_VALID', 'CONFIRMED', 'NOT_FOUND'));

-- Les profils déjà approuvés l'ont été par un administrateur après examen des justificatifs.
UPDATE professionals SET license_check_status = 'CONFIRMED', license_checked_at = reviewed_at, license_checked_by = reviewed_by
WHERE verification_status = 'APPROVED';
