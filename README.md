# Allo Docteur

Clinique virtuelle de soins à domicile : API Spring Boot + administration Angular.

## État – Phases 1 à 3
| Partie | État |
|---|---|
| `admin/` Angular 22 | login + 2FA, layout, utilisateurs, validation des professionnels, rendez-vous – **build et tests OK** |
| `backend/` Spring Boot 3.5.6 / Java 21 | Phases 1-2 (auth, utilisateurs, audit, référentiels, professionnels, recherche, validation admin) : `mvn verify` OK sur le poste du développeur. Phase 3 (patients, rendez-vous, téléconsultation, messagerie, documents) : **écrite, pas encore compilée ni testée** |
| Docker / CI | `docker-compose.yml`, `Makefile`, GitHub Actions – non exécutés |

Contrats d'API : `docs/api-contract-phase1.md`, `docs/api-contract-phase2.md`, `docs/api-contract-phase3.md`. Swagger : `/swagger-ui.html`.

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

- Référentiel géographique (villes/quartiers) en base ; jeu de départ : Abidjan et ses communes + 7 villes (modifiable par migration).
- Justificatifs stockés derrière le port `StorageService` (disque local `APP_STORAGE_PATH`, à monter en volume en production).
- Recherche : `Specification` JPA avec sous-requêtes `exists` (zones d'intervention, disponibilités, absences).
- Rendez-vous : confirmation manuelle par le professionnel ; créneaux calculés à la demande (pas stockés) ; anti-double réservation garanti par une contrainte d'exclusion PostgreSQL (`btree_gist`) en plus du contrôle applicatif.
- Modules découplés par événements (`AppointmentEvents`) et par identifiants : la téléconsultation ouvre sa salle en réagissant à `Started`, sans que le module rendez-vous la connaisse.
- Les administrateurs voient les rendez-vous (suivi) mais jamais les données de santé : détail du motif, date de naissance, messages et documents.
- Messagerie par polling (`since`) ; WebSocket prévu plus tard. Les messages sont chiffrés au repos ; les fichiers partagés ne le sont pas encore (phase de durcissement).
- Horloge injectée (`Clock`) : les tests d'intégration la déplacent pour atteindre l'heure d'un rendez-vous.

## À faire
- Écran admin de gestion des référentiels (spécialités, villes, quartiers).
- Notes/avis (`rating_avg`/`rating_count` sont prêts, alimentés en Phase 7).
- Imposer la 2FA aux comptes ADMIN SUPER/FINANCE (le champ `mfaEnabled` est exposé, le blocage n'est pas encore appliqué).
- Écrans admin d'activation 2FA et de réinitialisation de mot de passe.
