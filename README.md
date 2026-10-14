# Allo Docteur

Clinique virtuelle de soins à domicile : API Spring Boot + administration Angular.

## État – Phases 1 à 7
| Partie | État |
|---|---|
| `admin/` Angular 22 | login + 2FA, layout, utilisateurs, validation des professionnels, rendez-vous, journal d'accès médical (SUPER), suivi SOS, pharmacies, finance (paiements, retraits), abonnements, réclamations, modération des avis, partenaires – **build et tests OK** |
| `backend/` Spring Boot 3.5.6 / Java 21 | Phases 1-2 (auth, utilisateurs, audit, référentiels, professionnels, recherche, validation admin) : `mvn verify` OK sur le poste du développeur. Phase 3 (patients, rendez-vous, téléconsultation, messagerie, documents) Phase 4 (consentement, dossier médical, carnet de santé, journal d'accès, ordonnances) Phase 5 (SOS, pharmacies, livraison) et Phase 6 (paiements, portefeuille, commissions, abonnements) et Phase 7 (notifications, avis, réclamations, partenaires) : **écrites, pas encore compilées ni testées** |
| Docker / CI | `docker-compose.yml`, `Makefile`, GitHub Actions – non exécutés |

Design system de l'administration (tokens, composants, migration Material → Spartan terminée) : `docs/design-system.md`.

Contrats d'API : `docs/api-contract-phase1.md`, `docs/api-contract-phase2.md`, `docs/api-contract-phase3.md`, `docs/api-contract-phase4.md`, `docs/api-contract-phase5.md`, `docs/api-contract-phase6.md`, `docs/api-contract-phase7.md`. Swagger : `/swagger-ui.html`.

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
- Données de santé (phase 4) : accès à un dossier = propriétaire du compte, ou professionnel avec lien de soin (rendez-vous confirmé, en cours ou terminé) **et** consentement actif ; tout le reste → 404 ou 403. Les administrateurs n'y accèdent jamais ; seul le niveau SUPER voit le journal d'accès (métadonnées + IP). Journal en ajout seul (trigger PostgreSQL), refus compris. Texte de consentement v1.0 **provisoire** : à faire valider juridiquement.
- Ordonnances : numéro `ORD-AAAA-NNNNNN`, empreinte SHA-256 détectant une altération en base (ce n'est pas une signature électronique qualifiée), validité 90 jours, émission pendant la consultation ou 3 jours après (`app.prescriptions.*`).
- SOS (phase 5) : le premier professionnel de la même ville qui accepte gagne (UPDATE conditionnel atomique) et une visite urgente est créée. **Bris de glace** : le preneur accède au dossier sans consentement 24 h, motif obligatoire, journalisé ; à faire valider juridiquement. Structures de santé saisies par l'admin (aucune pré-remplie).
- Pharmacies : les pharmaciens sont des professionnels (type `PHARMACIST`) rattachés à une pharmacie par l'admin. Livraison derrière le port `DeliveryProvider` (stub) ; remise validée par un code à 6 chiffres, 5 essais max. Paiement des commandes en Phase 6, notifications en Phase 7.
- Paiements (phase 6) : paiement à la réservation, fonds en séquestre jusqu'à la prestation, puis répartition net/commission ; remboursement automatique en cas d'annulation. Commission globale (10 %) surchargeable par le plan d'abonnement du professionnel. Portefeuille à grand livre en ajout seul ; les gains des professionnels se retirent après validation de la finance. Opérateurs (Mobile Money, carte, banque) derrière `PaymentProvider`, **stubs uniquement** : aucune intégration réelle. Abonnements enregistrés sans blocage métier. Les prix des formules (5 000 / 15 000 / 2 000 XOF) et la commission de 10 % sont **indicatifs** : à valider avec le métier.
- Horloge injectée (`Clock`) : les tests d'intégration la déplacent pour atteindre l'heure d'un rendez-vous.
- Phase 7 : notifications in-app (cloche + compteur) doublées, pour les types sensibles au temps (rappel de rendez-vous, SOS, ordonnance, réclamation), d'un envoi push/SMS/e-mail via le port `NotificationChannel` (**stubs**). Les textes ne contiennent aucune donnée de santé. Avis : uniquement par le patient, après une consultation `COMPLETED`, un par rendez-vous ; la modération (masquer/rétablir, avec motif et audit) recalcule la note du professionnel. Réclamations : patients et professionnels, `OPEN → IN_REVIEW → RESOLVED | REJECTED`, fil de réponses chiffré visible du déposant, notification à chaque changement. Partenaires : annuaire + polices d'assurance enregistrées par le patient (numéro chiffré) ; aucun calcul de prise en charge sur les paiements.

## À faire
- Écran admin de gestion des référentiels (spécialités, villes, quartiers).
- Imposer la 2FA aux comptes ADMIN SUPER/FINANCE (le champ `mfaEnabled` est exposé, le blocage n'est pas encore appliqué).
- Écrans admin d'activation 2FA et de réinitialisation de mot de passe.
- Versement des gains des pharmacies (leur portefeuille est crédité, aucun retrait n'est encore prévu) et remboursement d'un paiement déjà réparti.
- Intégrations réelles Mobile Money / carte / banque (aujourd'hui : stubs), avec rappel de l'opérateur (webhook) pour les paiements en attente.
- Prorata lors d'un changement de formule d'abonnement ; effets métier des abonnements (visibilité, remises Premium) si le métier le décide.

## Phase 9 — durcissement
- **2FA obligatoire des administrateurs SUPER et FINANCE** : le jeton d'accès porte l'état de la 2FA (`mfa`). Tant qu'elle n'est pas activée, seul `/api/v1/auth/**` répond ; le reste renvoie `403` avec le code `MFA_ENROLLMENT_REQUIRED`. L'admin Angular conduit alors à `/security/2fa` (QR code, codes de secours). Premier démarrage : connectez-vous avec le compte initial, activez la 2FA, reconnectez-vous. `APP_REQUIRE_ADMIN_MFA=false` désactive l'exigence (développement uniquement).
