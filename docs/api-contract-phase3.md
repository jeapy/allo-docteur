# Contrat d'API – Phase 3 (patients, rendez-vous, téléconsultation, messagerie, documents)

Base : `/api/v1`. Toutes les routes exigent un jeton d'accès. Erreurs : RFC 7807 + `code`. Dates en ISO-8601 (UTC).

## Patient – mon espace (rôle PATIENT)
| Méthode | Chemin | Notes |
|---|---|---|
| GET / PUT | `/patients/me` | PUT = création ou mise à jour. GET renvoie un profil vide si rien n'est renseigné. `birthDate` dans le passé ; `districtId` doit appartenir à `cityId`. |
| GET / POST | `/patients/me/dependents` | Personnes à charge gérées par le compte (10 max). `relationship` : CHILD, SPOUSE, PARENT, OTHER |
| PUT / DELETE | `/patients/me/dependents/{id}` | 404 si la personne appartient à un autre compte. Suppression logique. |

## Créneaux (tout utilisateur connecté)
`GET /professionals/{id}/slots?type=&from=&to=` — `type` : TELECONSULTATION_VIDEO, TELECONSULTATION_AUDIO, CHAT, HOME_VISIT ; `from`/`to` au format `YYYY-MM-DD` (`to` facultatif = `from`), 14 jours maximum.

Les créneaux découlent des plages hebdomadaires du professionnel, moins ses absences et ses rendez-vous en cours. Durée : 30 min (téléconsultation, chat), 60 min (visite à domicile) ; préavis minimal 60 min ; réservation jusqu'à 60 jours à l'avance. Les plages sont interprétées dans le fuseau `app.appointments.zone` (Africa/Abidjan). Toutes ces valeurs sont réglables sous `app.appointments.*`.

Erreurs : 404 `PROFESSIONAL_NOT_FOUND` (non approuvé ou compte inactif), 400 `MODE_NOT_OFFERED`, 400 `RANGE_TOO_LARGE`.

## Rendez-vous
| Méthode | Chemin | Rôle | Notes |
|---|---|---|---|
| POST | `/appointments` | PATIENT | Réserve un créneau. Statut initial `REQUESTED`. |
| GET | `/appointments?status=&from=&to=&page=&size=&sort=` | PATIENT, PROFESSIONAL | Mes rendez-vous. `status` répétable. Tri : `startAt` (défaut, décroissant), `createdAt`, `status`. |
| GET | `/appointments/{id}` | participants, ADMIN | 404 pour un non-participant. L'admin ne reçoit ni `reasonDetails` ni `beneficiaryBirthDate`. |
| POST | `/appointments/{id}/confirm` | PROFESSIONAL concerné | `REQUESTED → CONFIRMED` |
| POST | `/appointments/{id}/cancel` `{reason?}` | PATIENT, PROFESSIONAL concerné, ADMIN SUPER/SUPPORT | `REQUESTED/CONFIRMED → CANCELLED`. Motif obligatoire sauf pour le patient. Un patient ne peut plus annuler un rendez-vous confirmé à moins de 2 h du début (`CANCELLATION_TOO_LATE`). L'annulation admin est tracée dans le journal d'audit. |
| POST | `/appointments/{id}/start` | PROFESSIONAL concerné | `CONFIRMED → IN_PROGRESS`, de 15 min avant le début jusqu'à la fin du créneau (`TOO_EARLY`, `TOO_LATE`). Ouvre la session audio/vidéo. |
| POST | `/appointments/{id}/complete` | PROFESSIONAL concerné | `IN_PROGRESS → COMPLETED`, ferme la session. |
| POST | `/appointments/{id}/no-show` | PROFESSIONAL concerné | `CONFIRMED → NO_SHOW`, 15 min après l'heure prévue. |

Corps de réservation : `professionalId, type, reason, startAt, reasonDetails?, dependentId?, address?, latitude?, longitude?`.
- `startAt` doit correspondre exactement à un créneau libre, sinon 409 `SLOT_UNAVAILABLE`. La base garantit l'absence de chevauchement par une contrainte d'exclusion : deux réservations simultanées donnent un seul 201.
- `reason` : CONSULTATION, TREATMENT_FOLLOWUP, MEDICAL_CERTIFICATE, PREGNANCY_FOLLOWUP, POSTNATAL, BREASTFEEDING_ADVICE (à distance ou à domicile) ; INJECTION, DRESSING, INFUSION, BLOOD_PRESSURE, MEDICAL_SURVEILLANCE, ELDERLY_ASSISTANCE, HYGIENE_CARE, DAILY_COMPANIONSHIP (visite à domicile uniquement, sinon 400 `REASON_REQUIRES_HOME_VISIT`).
- `HOME_VISIT` exige `address` (400 `ADDRESS_REQUIRED`) ; latitude et longitude vont ensemble.
- `dependentId` : personne à charge du compte ; le nom et la date de naissance sont figés dans le rendez-vous (`beneficiaryName`, `beneficiaryBirthDate`).
- Un même patient (ou une même personne à charge) ne peut pas avoir deux rendez-vous simultanés (409 `PATIENT_BUSY`).
- Le tarif (`fee`, `currency`) est celui du professionnel au moment de la réservation (téléconsultation/chat ou visite à domicile).
- `reasonDetails` est une donnée de santé : chiffrée au repos.

Transitions : `REQUESTED → CONFIRMED | CANCELLED` ; `CONFIRMED → IN_PROGRESS | CANCELLED | NO_SHOW` ; `IN_PROGRESS → COMPLETED`. Toute autre : 409 `INVALID_STATE`.

Administration : `GET /admin/appointments?status=&type=&patientId=&professionalId=&from=&to=&page=&size=&sort=` (rôle ADMIN).

## Téléconsultation, messagerie, documents (PATIENT et PROFESSIONAL du rendez-vous)
Aucune de ces routes n'est accessible aux administrateurs (403) : le contenu des échanges n'est pas consultable depuis l'administration. Un non-participant reçoit 404.

| Méthode | Chemin | Notes |
|---|---|---|
| POST | `/appointments/{id}/session` | Jeton d'accès à la salle `{media, roomId, joinUrl, token, tokenExpiresAt}`. Jeton personnel, valable 15 min. 409 `NO_MEDIA_SESSION` (chat, visite), 409 `SESSION_NOT_ACTIVE` (pas démarré ou terminé). |
| POST | `/appointments/{id}/messages` `{content}` | 4000 caractères max. 409 `CONVERSATION_CLOSED` si le rendez-vous n'est pas confirmé/en cours, ou terminé depuis plus de 7 jours (`app.messaging.post-completion-days`). |
| GET | `/appointments/{id}/messages?since=&limit=` | Ordre chronologique, 50 par défaut, 100 max. **Polling** : repasser `sentAt` du dernier message reçu dans `since` (exclu) et dédupliquer par `id`. Lecture toujours possible. |
| GET | `/appointments/{id}/messages/unread-count` | Messages de l'autre participant non lus. |
| POST | `/appointments/{id}/messages/read` | Marque comme lus les messages reçus ; renvoie `{count}`. |
| GET | `/appointments/{id}/documents` | Liste. |
| POST | `/appointments/{id}/documents` | Multipart `file` (+ `caption`). PDF, JPEG, PNG, WebP ; 10 Mo ; 30 par rendez-vous ; type détecté par signature. Mêmes conditions d'ouverture que les messages. |
| GET | `/appointments/{id}/documents/{docId}/content` | Téléchargement (`nosniff`, `attachment`). |
| DELETE | `/appointments/{id}/documents/{docId}` | Auteur seulement (403 `NOT_UPLOADER`), conversation ouverte. |

## Intégrations (ports)
`VideoProvider` : implémentation `FakeVideoProvider` par défaut (salle factice, jeton aléatoire, aucun appel réseau). Le service s'abonne aux événements `AppointmentEvents.Started` et `Completed` ; les événements `Requested`, `Confirmed`, `Cancelled`, `NoShow` sont déjà publiés pour les notifications (phase 7) et les paiements (phase 6).
