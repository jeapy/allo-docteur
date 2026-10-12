# Contrat d'API – Phase 1 (auth + utilisateurs)

Base : `/api/v1`. Erreurs : RFC 7807 (`ProblemDetail`) avec un champ `code` stable.
L'admin Angular est déjà écrit contre ce contrat ; le backend doit le respecter.

| Méthode | Chemin | Corps | Réponse |
|---|---|---|---|
| POST | `/auth/login` | `{email, password}` | `{status:"AUTHENTICATED", accessToken, expiresIn, user}` ou `{status:"MFA_REQUIRED", mfaToken}` ; pose le cookie `refresh_token` (httpOnly, Secure, SameSite=Strict, path `/api/v1/auth`) quand authentifié |
| POST | `/auth/2fa/verify` | `{mfaToken, code}` | idem `AUTHENTICATED` |
| POST | `/auth/refresh` | cookie (admin web) ou `{refreshToken}` (mobile) | `{accessToken, expiresIn}` ; rotation du refresh token, détection de réutilisation |
| POST | `/auth/logout` | cookie | 204, révoque la famille de tokens |
| GET | `/auth/me` | — | `CurrentUser` |
| GET | `/admin/users` | query : `page,size,sort,q,role,status` | `Page<UserSummary>` (`content,totalElements,number,size`) |
| PATCH | `/admin/users/{id}/status` | `{status}` | `UserSummary` |

`user` / `CurrentUser` : `{id,email,firstName,lastName,role,adminLevel}`.
Codes 429 sur `/auth/*` au-delà du quota (Bucket4j). 2FA obligatoire pour les admins SUPER et FINANCE.
