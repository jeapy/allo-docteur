# Contrat d'API – Phase 7 (notifications, avis, réclamations, partenaires)

Base : `/api/v1`. Jeton d'accès requis. Erreurs : RFC 7807 + `code`. Pagination : `page`, `size` (max 100) → `{content, totalElements, number, size}`.

## Notifications (PATIENT, PROFESSIONAL ; ADMIN → 403)
Créées par les événements métier ; chacune est stockée in-app. Les types `APPOINTMENT_REMINDER`, `SOS`, `PRESCRIPTION`, `COMPLAINT_UPDATE` (`app.notifications.external-types`) partent en plus par les canaux externes (push, SMS, e-mail – **stubs**) **après** validation de la transaction. Aucun texte ne contient de donnée de santé.

| Méthode | Chemin | Notes |
|---|---|---|
| GET | `/notifications?unread=false` | Plus récentes d'abord. `{id, type, title, body, refType, refId, read, createdAt}`. |
| GET | `/notifications/unread-count` | `{count}`. |
| POST | `/notifications/{id}/read` | 404 si la notification n'est pas à l'appelant. |
| POST | `/notifications/read-all` | `{updated}`. |

Types : `APPOINTMENT`, `APPOINTMENT_REMINDER`, `PAYMENT`, `PRESCRIPTION`, `SOS`, `PHARMACY`, `SUBSCRIPTION`, `REVIEW`, `COMPLAINT_UPDATE`, `PROFESSIONAL`, `SYSTEM`. `refType`/`refId` permettent d'ouvrir l'objet concerné (`APPOINTMENT`, `PAYMENT`, `SOS`, `PHARMACY_ORDER`, `PRESCRIPTION`, `SUBSCRIPTION`, `REVIEW`, `COMPLAINT`, `PROFESSIONAL`).

Rappels : tâche toutes les 5 min ; un rendez-vous `CONFIRMED` qui commence dans `app.notifications.reminder-minutes` (60) prévient patient et professionnel, une seule fois (table `appointment_reminders`).

## Avis
| Méthode | Chemin | Rôle | Notes |
|---|---|---|---|
| POST | `/appointments/{id}/review` `{rating 1..5, comment?≤2000}` | PATIENT | Le patient du rendez-vous, une fois `COMPLETED`. 404 si non concerné ; 403 pour le professionnel ; 409 `REVIEW_NOT_ALLOWED` (pas terminé) ; 409 `ALREADY_REVIEWED`. Auteur affiché « Prénom N. ». |
| GET | `/professionals/{id}/reviews` | tous | Avis `VISIBLE` seulement. |
| GET | `/reviews/mine` | PATIENT | Mes avis. |

`rating_avg` (2 décimales) et `rating_count` du professionnel sont recalculés atomiquement à chaque dépôt ou modération.

### Administration (SUPER, SUPPORT)
| Méthode | Chemin | Notes |
|---|---|---|
| GET | `/admin/reviews?status&professionalId` | Tous les avis, masqués compris. |
| POST | `/admin/reviews/{id}/hide` `{reason}` | Motif obligatoire, audit `REVIEW_HIDDEN`. 409 si déjà masqué. |
| POST | `/admin/reviews/{id}/show` | Rétablit, audit `REVIEW_RESTORED`. |

## Réclamations (PATIENT, PROFESSIONAL)
Numéro `CLM-AAAA-NNNNNN`. Cycle : `OPEN → IN_REVIEW → RESOLVED | REJECTED`. Description, résolution et messages chiffrés au repos. Un non-auteur reçoit 404.

| Méthode | Chemin | Notes |
|---|---|---|
| POST | `/complaints` `{subject≤255, description≤5000, appointmentId?}` | Le rendez-vous doit concerner l'auteur (sinon 404). Renvoie le détail. |
| GET | `/complaints/mine` | Page de résumés. |
| GET | `/complaints/{id}` | `{summary, description, resolution, messages[]}`. |
| POST | `/complaints/{id}/messages` `{body}` | 409 `COMPLAINT_CLOSED` si clôturée. |

### Administration (SUPER, SUPPORT)
| Méthode | Chemin | Notes |
|---|---|---|
| GET | `/admin/complaints?status` | Filtre ; les plus anciennes d'abord quand un statut est donné. |
| GET | `/admin/complaints/summary` | `{OPEN, IN_REVIEW, RESOLVED, REJECTED}` : effectifs. |
| GET | `/admin/complaints/{id}` | Détail avec fil. |
| POST | `/admin/complaints/{id}/take` | `OPEN → IN_REVIEW`, assignation. 409 sinon. |
| POST | `/admin/complaints/{id}/messages` `{body}` | Réponse visible du déposant ; prend en charge une réclamation ouverte. |
| POST | `/admin/complaints/{id}/resolve` `{text}` | → `RESOLVED`. |
| POST | `/admin/complaints/{id}/reject` `{text}` | → `REJECTED` ; le motif est la résolution. |

Chaque prise en charge, réponse, résolution ou rejet est audité et notifie le déposant (`COMPLAINT_UPDATE`).

## Partenaires et assurances
Types : `CLINIC`, `HEALTH_CENTER`, `HOSPITAL`, `PHARMACY`, `LAB`, `AMBULANCE`, `INSURANCE`, `MUTUAL`. Statut : `ACTIVE`, `INACTIVE`.

| Méthode | Chemin | Rôle | Notes |
|---|---|---|---|
| GET | `/partners?type&cityId` | PATIENT, PROFESSIONAL | Partenaires actifs. |
| GET/POST | `/admin/partners` | SUPER, SUPPORT | Liste filtrable (`status`, `type`, `cityId`) / création `{name, type, cityId?, address?, contactEmail?, contactPhone?, contractStartDate?}`. |
| GET/PUT | `/admin/partners/{id}` | SUPER, SUPPORT | |
| POST | `/admin/partners/{id}/activate` · `/deactivate` | SUPER, SUPPORT | Audité. |

### Polices d'assurance (PATIENT)
| Méthode | Chemin | Notes |
|---|---|---|
| GET/POST | `/insurance-policies` | `{partnerId, policyNumber, coverageRate 0..100, validUntil, dependentId?}`. Le partenaire doit être un **assureur ou une mutuelle actifs** (400 `INVALID_PARTNER`) ; `validUntil` ne peut être dans le passé (400 `POLICY_EXPIRED`) ; personne à charge inconnue → 404. |
| GET/PUT/DELETE | `/insurance-policies/{id}` | 404 si la police n'est pas au patient. Numéro chiffré au repos. `expired` calculé. |

Enregistrement seulement : aucun calcul de prise en charge n'est appliqué aux paiements.
