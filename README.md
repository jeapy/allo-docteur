# Allo Docteur

Clinique virtuelle de soins à domicile : **API Spring Boot** (qui servira aussi l'application mobile Flutter, hors périmètre ici) et **interface d'administration Angular**.

## État du projet
| Partie | État |
|---|---|
| `admin/` Angular 22 (Spartan UI + Tailwind 4) | Tous les écrans du §6.9 du cahier des charges + gestion des référentiels, structures de santé, journal d'audit, réinitialisation de mot de passe, enrôlement 2FA. `npm test` (62 tests) et `ng build` passent. |
| `backend/` Spring Boot 3.5.6 / Java 21 | Phases 1-2 : `mvn verify` confirmé sur le poste du développeur. Phases 3 à 9 (rendez-vous, dossier médical, SOS, pharmacie, paiements, avis, réclamations, 2FA admin obligatoire, performance, journal d'audit, référentiels, réinitialisation par un admin) : **écrites mais jamais compilées ni testées dans l'environnement de l'assistant** — lancer `mvn verify` et remonter les erreurs. |
| Docker / CI | `docker-compose.yml`, `Makefile`, GitHub Actions (`.github/workflows/ci.yml`) — non exécutés. |

## Démarrage rapide
```
cp .env.example .env     # puis remplir (make secrets génère les secrets)
make up                  # postgres + backend (8080) + admin (8081)
```
- Swagger : <http://localhost:8080/swagger-ui.html> · santé : `/actuator/health`.
- Compte admin initial : `APP_ADMIN_EMAIL` / `APP_ADMIN_PASSWORD`, créé au premier démarrage. **Il doit activer la 2FA à sa première connexion** (l'admin Angular l'y conduit) ; en développement seulement, `APP_REQUIRE_ADMIN_MFA=false` lève cette exigence.

## Développement
- Backend : Java 21, Maven, PostgreSQL 16. `cd backend && mvn verify` (tests d'intégration via Testcontainers : Docker requis).
- Admin : Node ≥ 22.22.3 (24 recommandé, comme la CI). `cd admin && npm ci && npm start` (proxy `/api` vers `localhost:8080`), `npm test`, `npm run build`.
- `make test` lance les deux suites.

## Documentation
| Document | Contenu |
|---|---|
| [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) | Modules, ports et stubs, modèle de données, migrations, sécurité, décisions |
| [`docs/EXPLOITATION.md`](docs/EXPLOITATION.md) | Variables d'environnement, premier démarrage, sauvegardes, checklist de production, limites connues |
| [`docs/api-contract-phase1.md`](docs/api-contract-phase1.md) … [`phase9`](docs/api-contract-phase9.md) | Contrat d'API de chaque phase (Swagger fait foi pour le détail des schémas) |
| [`docs/api-contract-phase10.md`](docs/api-contract-phase10.md) | Candidature publique d'un professionnel et vérification du n° d'ordre |
| [`docs/design-system.md`](docs/design-system.md) | Design system de l'administration, composants, pièges rencontrés |
| [`docs/schema_bdd_allo_docteur.md`](docs/schema_bdd_allo_docteur.md) | Schéma de départ proposé (le schéma réel est celui des migrations Flyway) |

## Ce qui reste à faire (hors périmètre des phases réalisées)
- **Intégrations réelles** derrière les ports (aujourd'hui des stubs) : Mobile Money, carte bancaire, virement, vidéo, SMS/e-mail/push, géolocalisation inverse, livraison. Aucune n'a été branchée sans accord.
- **Validation juridique** : texte de consentement v1.0 (provisoire), bris de glace du SOS, valeur des ordonnances électroniques.
- **Validation métier** : prix des formules d'abonnement et commission de 10 % (indicatifs).
- Chiffrement au repos des **fichiers** partagés (les champs médicaux et messages le sont déjà), rotation de la clé de chiffrement.
- Audit de sécurité externe (OWASP), escalade automatique des SOS sans réponse, versement des gains des pharmacies, remboursement d'un paiement déjà réparti, prorata des changements de formule, couverture de tests mesurée (JaCoCo).
- Recherche globale et cloche de l'en-tête admin : visuelles seulement (endpoints à créer).
- Migration vers Spring Boot 4.x.

Le détail des décisions prises phase par phase est dans [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md#décisions-de-conception).
