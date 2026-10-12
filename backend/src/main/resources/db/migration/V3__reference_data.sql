-- Référentiels : types de professionnels, spécialités, géographie

CREATE TABLE professional_types (
    id       INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    code     VARCHAR(50)  NOT NULL UNIQUE,
    label_fr VARCHAR(100) NOT NULL
);

CREATE TABLE specialties (
    id                  INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    code                VARCHAR(60)  NOT NULL UNIQUE,
    name_fr             VARCHAR(120) NOT NULL,
    parent_specialty_id INTEGER REFERENCES specialties (id),
    active              BOOLEAN NOT NULL DEFAULT TRUE
);
CREATE INDEX ix_specialties_parent ON specialties (parent_specialty_id);

CREATE TABLE cities (
    id           INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name         VARCHAR(120) NOT NULL,
    country_code VARCHAR(2)   NOT NULL DEFAULT 'CI',
    active       BOOLEAN      NOT NULL DEFAULT TRUE,
    CONSTRAINT ux_cities_name UNIQUE (country_code, name)
);

CREATE TABLE districts (
    id      INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    city_id INTEGER      NOT NULL REFERENCES cities (id),
    name    VARCHAR(120) NOT NULL,
    active  BOOLEAN      NOT NULL DEFAULT TRUE,
    CONSTRAINT ux_districts_city_name UNIQUE (city_id, name)
);

INSERT INTO professional_types (code, label_fr) VALUES
    ('GENERAL_PRACTITIONER', 'Médecin généraliste'),
    ('SPECIALIST_DOCTOR',    'Médecin spécialiste'),
    ('NURSE',                'Infirmier(ère)'),
    ('MIDWIFE',              'Sage-femme'),
    ('CARE_ASSISTANT',       'Aide-soignant(e)'),
    ('PHYSIOTHERAPIST',      'Kinésithérapeute'),
    ('PSYCHOLOGIST',         'Psychologue'),
    ('NUTRITIONIST',         'Nutritionniste');

INSERT INTO specialties (code, name_fr) VALUES
    ('CARDIOLOGY',        'Cardiologie'),
    ('PEDIATRICS',        'Pédiatrie'),
    ('GYNECOLOGY',        'Gynécologie-obstétrique'),
    ('DERMATOLOGY',       'Dermatologie'),
    ('OPHTHALMOLOGY',     'Ophtalmologie'),
    ('ENT',               'Oto-rhino-laryngologie (ORL)'),
    ('PSYCHIATRY',        'Psychiatrie'),
    ('NEUROLOGY',         'Neurologie'),
    ('GASTROENTEROLOGY',  'Gastro-entérologie'),
    ('ENDOCRINOLOGY',     'Endocrinologie-diabétologie'),
    ('PULMONOLOGY',       'Pneumologie'),
    ('UROLOGY',           'Urologie'),
    ('RHEUMATOLOGY',      'Rhumatologie'),
    ('GERIATRICS',        'Gériatrie'),
    ('GENERAL_MEDICINE',  'Médecine générale');

-- Jeu de départ modifiable (ajouts/retraits via migrations ou futur écran d'administration)
INSERT INTO cities (name) VALUES
    ('Abidjan'), ('Bouaké'), ('Yamoussoukro'), ('San-Pédro'), ('Daloa'), ('Korhogo'), ('Man'), ('Gagnoa');

INSERT INTO districts (city_id, name)
SELECT c.id, d.name FROM cities c
JOIN (VALUES ('Abobo'), ('Adjamé'), ('Anyama'), ('Attécoubé'), ('Bingerville'), ('Cocody'), ('Koumassi'),
             ('Marcory'), ('Plateau'), ('Port-Bouët'), ('Songon'), ('Treichville'), ('Yopougon')) AS d(name)
  ON c.name = 'Abidjan';
