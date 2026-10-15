# Contrat d'API — Phase 10 : candidature publique et vérification du n° d'ordre

Swagger fait foi pour les schémas. Erreurs : RFC 7807 avec champ `code`.

## Public (sans JWT, limité à 20 req/min/IP → 429 `RATE_LIMITED`)

| Méthode | Route | Rôle |
|---|---|---|
| GET | `/api/v1/reference/{professional-types,specialties,cities}` | Référentiels, désormais lisibles sans authentification |
| GET | `/api/v1/public/license-check?typeId=&number=` | Contrôle à blanc : `status` ∈ `INVALID_FORMAT`, `ALREADY_REGISTERED`, `FORMAT_VALID`, `REGISTRY_CONFIRMED`, `REGISTRY_NOT_FOUND` + `message` |
| POST | `/api/v1/public/professional-applications` | Candidature, `multipart/form-data` → 201 `{reference, email, status: PENDING}` |

Parties du POST : `application` (JSON : `account{email,password(≥10),firstName,lastName,phone E.164 obligatoire}`, `profile` = corps de `PUT /professionals/me`, `acceptTerms=true`), `types` (un par fichier, même ordre : `LICENSE|DIPLOMA|ID_CARD|OTHER`), `files` (≤ 5, PDF/JPEG/PNG, ≤ 5 Mo).

Codes d'erreur : `VALIDATION_ERROR`, `DOCUMENTS_REQUIRED`, `LICENSE_DOCUMENT_REQUIRED`, `TOO_MANY_DOCUMENTS`, `DOCUMENT_TYPES_MISMATCH`, `UNSUPPORTED_FILE`, `FILE_TOO_LARGE`, `UPLOAD_TOO_LARGE` (413), `INVALID_LICENSE_FORMAT`, `LICENSE_ALREADY_USED` (409), `EMAIL_ALREADY_USED` (409), `PHONE_ALREADY_USED` (409).

## Admin

| Méthode | Route | Rôle |
|---|---|---|
| POST | `/api/v1/admin/professionals/{id}/license-check` | SUPER/SUPPORT. Corps `{result: CONFIRMED|NOT_FOUND, note?}` → détail du professionnel |

Le détail (`licenseCheck{status,checkedAt,checkedBy,note}`) et la liste (`licenseCheck`) exposent le statut `UNCHECKED|FORMAT_VALID|CONFIRMED|NOT_FOUND`. `POST …/approve` répond 409 `LICENSE_NOT_FOUND` si le numéro est signalé introuvable, et 409 `LICENSE_NOT_CONFIRMED` si `app.professionals.require-confirmed-license=true` et le numéro n'est pas confirmé. Audit : `PROFESSIONAL_APPLICATION_SUBMITTED`, `PROFESSIONAL_LICENSE_CONFIRMED`, `PROFESSIONAL_LICENSE_NOT_FOUND`.
