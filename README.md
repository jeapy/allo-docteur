# Allo Docteur

Clinique virtuelle de soins à domicile : API Spring Boot + administration Angular.

## État (Phase 1, en cours)
- `admin/` : Angular 22 (login + 2FA, layout sidenav, liste des utilisateurs paginée/triable/filtrable côté serveur, JWT + refresh, guards, i18n fr). Build et tests OK.
- `backend/` : à venir (bloqué : Maven Central inaccessible depuis l'environnement de développement).
- `docs/` : cahier des charges, schéma de BDD, `api-contract-phase1.md`.

## Admin
Node >= 22.22.3 requis par Angular 22.
```
cd admin && npm ci
npm start            # proxy /api -> http://localhost:8080
npm test             # vitest
npm run build
```
