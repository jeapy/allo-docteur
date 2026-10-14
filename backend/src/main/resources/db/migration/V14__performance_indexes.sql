-- Phase 9 — performance : index complémentaires (aucun changement de schéma applicatif)

-- Recherche de disponibilités : filtre par jour puis tri par heure de début pour un professionnel.
CREATE INDEX ix_avail_rules_prof_day ON availability_rules (professional_id, day_of_week, start_time);

-- SOS / pharmacies : recherche du plus proche par rectangle englobant (voir NearestSearch).
CREATE INDEX ix_facilities_geo ON health_facilities (latitude, longitude) WHERE active AND emergency_service;
CREATE INDEX ix_pharmacies_geo ON pharmacies (latitude, longitude) WHERE active AND latitude IS NOT NULL;

-- Recherche texte « contient » (LIKE '%x%') de l'administration : index trigrammes.
CREATE EXTENSION IF NOT EXISTS pg_trgm;
CREATE INDEX ix_users_email_trgm ON users USING gin (lower(email) gin_trgm_ops);
CREATE INDEX ix_users_first_name_trgm ON users USING gin (lower(first_name) gin_trgm_ops);
CREATE INDEX ix_users_last_name_trgm ON users USING gin (lower(last_name) gin_trgm_ops);
CREATE INDEX ix_pharmacies_name_trgm ON pharmacies USING gin (lower(name) gin_trgm_ops);
