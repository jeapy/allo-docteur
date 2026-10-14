# Contrat d'API – Phase 9 (durcissement et compléments d'administration)

Base : `/api/v1`. Jeton d'accès requis sauf mention contraire. Erreurs : RFC 7807 + `code`. Pagination : `page`, `size` (max 100) → `{content, totalElements, number, size}`. Swagger fait foi pour les schémas.

## 2FA obligatoire des administrateurs SUPER et FINANCE
Le jeton d'accès porte `mfa` (2FA activée ou non). Tant qu'elle manque, **toute route hors `/auth/**` répond** `403` avec `code = MFA_ENROLLMENT_REQUIRED`. L'enrôlement utilise les routes existantes : `POST /auth/2fa/setup` → `POST /auth/2fa/enable {code}` (renvoie les codes de secours). Le jeton déjà émis reste bloqué : refaire `POST /auth/refresh` (ou se reconnecter) pour obtenir un jeton avec `mfa = true`. Les admins SUPPORT, les patients et les professionnels ne sont pas concernés. Désactivable par `APP_REQUIRE_ADMIN_MFA=false` (développement).

## Journal d'audit général — SUPER
| Méthode | Chemin | Notes |
|---|---|---|
| GET | `/admin/audit-logs` | Filtres : `action`, `targetType`, `targetId`, `actorId` (UUID), `from`, `to` (instants ISO, `to` exclusif), `page`, `size`. Plus récents d'abord. `{id, actorId, actorEmail, action, targetType, targetId, ipAddress, details, createdAt}` ; `actorEmail` est nul pour un événement système. |
| GET | `/admin/audit-logs/actions` | Liste des actions déjà enregistrées (alimente le filtre). |

Le journal est en ajout seul (trigger PostgreSQL) : aucune route de modification ni de suppression.

## Réinitialisation du mot de passe par un administrateur — SUPER, SUPPORT
| Méthode | Chemin | Notes |
|---|---|---|
| POST | `/admin/users/{id}/password-reset` | `202`, corps vide. Envoie à l'adresse du compte un lien `…/reset-password?token=…` valable 1 heure. L'administrateur ne voit **jamais** le lien. `404 USER_NOT_FOUND`. Journalisé : `PASSWORD_RESET_TRIGGERED_BY_ADMIN`. |

Côté titulaire (inchangé, public) : `POST /auth/password/forgot {email}` → `202` identique que le compte existe ou non ; `POST /auth/password/reset {token, newPassword}` (10 à 128 caractères) → `204`, révoque les sessions ; `400 INVALID_RESET_TOKEN` si le lien est invalide, expiré ou déjà utilisé.

## Référentiels — SUPER
Rien n'est supprimé : désactiver retire l'élément des listes publiques (`/reference/*`) sans toucher à l'existant. Les libellés sont nettoyés (espaces) ; les doublons sont refusés sans tenir compte de la casse (`409`).

| Méthode | Chemin | Notes |
|---|---|---|
| GET | `/admin/reference/cities` | Toutes les villes (actives ou non) avec leurs quartiers : `{id, name, countryCode, active, districts:[{id, name, active}]}`. |
| POST | `/admin/reference/cities` `{name}` | `201`. `409 CITY_EXISTS`. |
| PUT | `/admin/reference/cities/{id}` `{name}` | Renommer. |
| POST | `/admin/reference/cities/{id}/activate` · `/deactivate` | |
| POST | `/admin/reference/cities/{id}/districts` `{name}` | `201`, renvoie la ville mise à jour. `409 DISTRICT_EXISTS`. |
| PUT | `/admin/reference/districts/{id}` `{name}` | Renvoie la ville. |
| POST | `/admin/reference/districts/{id}/activate` · `/deactivate` | Renvoie la ville. |
| GET / POST | `/admin/reference/specialties` | `{id, code, name, active}` ; création `{code, name}` (`409 SPECIALTY_EXISTS`). |
| PUT | `/admin/reference/specialties/{id}` `{name}` | Le code ne change pas. |
| POST | `/admin/reference/specialties/{id}/activate` · `/deactivate` | |
| GET / POST | `/admin/reference/professional-types` | `{id, code, label}` ; création `{code, name}` (`409 TYPE_EXISTS`). |
| PUT | `/admin/reference/professional-types/{id}` `{name}` | Le code est utilisé par les règles métier (ex. `PHARMACIST`) : seul le libellé change. |

Codes : 2 à 50 caractères parmi `A-Z`, `0-9`, `_` (mis en majuscules) — sinon `400 INVALID_CODE`. Libellé vide : `400 NAME_REQUIRED`.

## Performance
Migration `V14` : index de disponibilités, index géographiques partiels, index trigrammes (`pg_trgm`) pour la recherche d'utilisateurs et de pharmacies. Sans changement de contrat : `GET /health-facilities/nearby` et `GET /pharmacies/nearby` renvoient le même résultat qu'avant, calculé sans charger toute la table.
