# Allo Docteur

Clinique virtuelle de soins à domicile : API Spring Boot + administration Angular.

## État – Phase 1
| Partie | État |
|---|---|
| `admin/` Angular 22 | login + 2FA, layout, liste des utilisateurs – **build et tests OK** |
| `backend/` Spring Boot 3.5.6 / Java 21 | auth (JWT, refresh avec rotation, TOTP, reset), utilisateurs, audit, tests – **écrit, pas encore compilé ni testé** |
| Docker / CI | `docker-compose.yml`, `Makefile`, GitHub Actions – non exécutés |

Contrat d'API : `docs/api-contract-phase1.md`. Swagger : `/swagger-ui.html`.

## Démarrage rapide
```
cp .env.example .env     # puis remplir (make secrets génère les secrets)
make up                  # postgres + backend (8080) + admin (8081)
```
Compte admin initial : `APP_ADMIN_EMAIL` / `APP_ADMIN_PASSWORD` (créé au premier démarrage).

## Développement
- Backend : Java 21, Maven, PostgreSQL 16. `cd backend && mvn verify` (tests d'intégration via Testcontainers : Docker requis).
- Admin : Node >= 22.22.3. `cd admin && npm ci && npm start` (proxy `/api` vers `localhost:8080`), `npm test`.

## Décisions notables
- Refresh token : cookie httpOnly/SameSite=Strict pour le web, corps de réponse si l'en-tête `X-Client-Type: mobile` est présent. Rotation + détection de réutilisation (toute la famille est révoquée).
- TOTP RFC 6238 implémenté sans dépendance externe ; secret chiffré AES-256-GCM au repos ; codes de secours hachés.
- Verrouillage du compte 15 min après 5 échecs ; limitation de débit par IP sur `POST /auth/**` (en mémoire).
- E-mails derrière le port `EmailSender` (stub qui journalise).
- Spring Boot 3.5.6 choisi faute de pouvoir compiler en environnement restreint ; migration vers la 4.x prévue en phase de durcissement.

## À faire (Phase 1)
- Imposer la 2FA aux comptes ADMIN SUPER/FINANCE (le champ `mfaEnabled` est exposé, le blocage n'est pas encore appliqué).
- Écrans admin d'activation 2FA et de réinitialisation de mot de passe.
