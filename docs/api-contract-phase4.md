# Contrat d'API – Phase 4 (consentement, dossier médical, carnet de santé, ordonnances)

Base : `/api/v1`. Toutes les routes exigent un jeton d'accès. Erreurs : RFC 7807 + `code`. Dates en ISO-8601 (UTC).
Pagination : `page`, `size` (max 100) ; réponse `{content, totalElements, number, size}`.

## Règle d'accès à un dossier
| Demandeur | Accès |
|---|---|
| Titulaire du compte (patient/tuteur) | Toujours, à son dossier et à ceux de ses personnes à charge. |
| Professionnel | Seulement s'il a un rendez-vous **confirmé, en cours ou terminé** avec la personne **et** que le titulaire a un consentement actif pour la version courante. |
| Autre patient / professionnel sans lien de soin | 404 `RECORD_NOT_FOUND` (l'existence n'est pas révélée). |
| Administrateur (tout niveau) | Jamais (403). |

Chaque écriture, chaque accès d'un tiers et chaque refus sont inscrits au journal d'accès. Les lectures du propriétaire ne sont pas journalisées.

## Consentement (rôle PATIENT)
| Méthode | Chemin | Notes |
|---|---|---|
| GET | `/consents/current` | Texte courant (`version, title, content, publishedAt`) et état (`accepted, acceptedAt`). |
| POST | `/consents` `{version}` | Accepte la version courante ; idempotent. Une version périmée → 409 `CONSENT_VERSION_OUTDATED`. Un consentement d'une version antérieure est remplacé. |
| DELETE | `/consents/current` | Retire le consentement (204) : les professionnels perdent l'accès immédiatement. |
| GET | `/consents/history` | Historique horodaté (`version, grantedAt, revokedAt`). |

Une nouvelle version du texte se publie par migration Flyway (`consent_versions`) : les patients doivent la ré-accepter.

## Dossier médical (PATIENT, PROFESSIONAL)
| Méthode | Chemin | Rôle | Notes |
|---|---|---|---|
| GET | `/medical-records/me` | PATIENT | Mon dossier (créé à la première demande). |
| GET | `/medical-records/dependents/{dependentId}` | PATIENT | Dossier d'une personne à charge ; 404 si elle n'est pas à moi. |
| GET | `/appointments/{id}/medical-record` | PROFESSIONAL | Dossier de la personne concernée par le rendez-vous. 404 si non participant ; 403 `CONSENT_REQUIRED` sans consentement. |
| GET | `/medical-records/{id}` | tous deux | Règle d'accès ci-dessus. |
| PUT | `/medical-records/{id}/summary` | PATIENT propriétaire | `bloodGroup` (A_POS…O_NEG), `allergies`, `chronicConditions`, `currentMedications`, `familyHistory`. Un professionnel reçoit 403 `OWNER_ONLY`. |
| GET | `/medical-records/{id}/access-log?page=&size=` | PATIENT propriétaire | Qui a consulté/modifié le dossier, et quand (sans adresse IP). |

La fiche de synthèse, les titres et contenus d'entrées sont chiffrés au repos (AES-256-GCM).

## Carnet de santé
| Méthode | Chemin | Notes |
|---|---|---|
| GET | `/medical-records/{id}/entries?kind=&page=&size=` | Tri : date de l'événement puis création, décroissants. |
| POST | `/medical-records/{id}/entries` | `{kind, title, content?, occurredOn?, appointmentId?}`. 201. |
| DELETE | `/medical-records/{id}/entries/{entryId}` | PATIENT auteur seulement (suppression logique). Les notes d'un professionnel sont conservées : 403 `ENTRY_IMMUTABLE`. |

Types (`kind`) : professionnel → CONSULTATION_NOTE, DIAGNOSIS, VACCINATION, MEASUREMENT, LAB_RESULT, HOSPITALIZATION ; patient → VACCINATION, MEASUREMENT, LAB_RESULT, HOSPITALIZATION, PERSONAL_NOTE. Sinon 403 `ENTRY_KIND_NOT_ALLOWED`.
`appointmentId` doit concerner la même personne que le dossier (400 `APPOINTMENT_MISMATCH`).

## Ordonnances
| Méthode | Chemin | Rôle | Notes |
|---|---|---|---|
| POST | `/appointments/{id}/prescriptions` | PROFESSIONAL du rendez-vous | `{items:[{medicationName, dosage?, frequency?, durationDays?, quantity?, instructions?}], notes?}` – 1 à 20 lignes. 201. |
| GET | `/prescriptions?page=&size=` | PATIENT, PROFESSIONAL | Reçues (patient) ou émises (professionnel). **Résumés sans médicament ni note.** |
| GET | `/prescriptions/{id}` | règle d'accès au dossier | Détail complet, `expired`, `integrityValid`. |
| GET | `/medical-records/{recordId}/prescriptions` | règle d'accès au dossier | Détails des ordonnances du dossier. |
| POST | `/prescriptions/{id}/cancel` `{reason}` | PROFESSIONAL émetteur | `ISSUED → CANCELLED` ; 409 `PRESCRIPTION_ALREADY_CANCELLED`. L'ordonnance reste consultable. |

Règles d'émission :
- Profession habilitée (`app.prescriptions.allowed-professional-types`, défaut : médecin généraliste, médecin spécialiste, sage-femme) sinon 403 `PRESCRIBER_NOT_ALLOWED`.
- Rendez-vous `IN_PROGRESS`, ou `COMPLETED` depuis moins de `issue-window-days` (3) jours, sinon 409 `PRESCRIPTION_NOT_ALLOWED`.
- Consentement actif du patient (403 `CONSENT_REQUIRED`).
- Validité `validity-days` (90). Numéro `ORD-AAAA-NNNNNN` (séquence PostgreSQL). Médicaments, posologie, consignes et notes chiffrés au repos.
- `integrityHash` (SHA-256 du contenu) recalculé à chaque lecture : `integrityValid=false` signale une altération en base. Ce n'est pas une signature électronique qualifiée (suite possible : PDF signé).

## Administration (niveau SUPER)
`GET /admin/medical-access-logs?recordId=&actorId=&outcome=GRANTED|DENIED&from=&to=&page=&size=` – métadonnées seulement (acteur, action, résultat, motif de refus, adresse IP), tri par date décroissante. Les journaux sont en ajout seul : un trigger PostgreSQL interdit UPDATE et DELETE.

Motifs de refus (`reason`) : `NOT_OWNER`, `OWNER_ONLY`, `NO_CARE_RELATIONSHIP`, `NO_CONSENT`.

## Migrations
- `V8__consent_medical_records.sql` : consent_versions (v1.0 provisoire), consents (un seul actif par compte), medical_records, medical_record_entries, medical_access_logs (+ trigger).
- `V9__prescriptions.sql` : séquence `prescription_number_seq`, prescriptions, prescription_items.
