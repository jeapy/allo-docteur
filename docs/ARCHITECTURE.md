# Architecture

## Vue d'ensemble
```
 Flutter (hors périmètre) ──┐
                            ├──►  API REST Spring Boot  ──►  PostgreSQL 16
 Admin Angular (Nginx) ─────┘     (monolithe modulaire)        (Flyway, ddl-auto=validate)
```
- **Backend** : Java 21, Spring Boot 3.5.6, Spring Security (JWT access + refresh), Spring Data JPA, Flyway, MapStruct, Lombok, springdoc-openapi, Actuator.
- **Admin** : Angular 22 (composants standalone, signaux, routes chargées à la demande), Spartan UI + Tailwind 4, Transloco (français), RxJS.
- **Docker** : `postgres`, `backend`, `admin` (statique servi par Nginx) — voir `docker-compose.yml`.

## Backend : monolithe modulaire
Package racine `dev.jpzconception.allodocteur`. Chaque module métier se découpe en `api` (contrôleurs, DTO), `application` (services, cas d'usage), `domain` (entités, énumérations, règles), `infrastructure` (dépôts, adaptateurs).

| Module | Rôle |
|---|---|
| `auth`, `user` | Inscription, connexion, refresh, 2FA TOTP, mot de passe oublié, comptes |
| `reference` | Villes, quartiers, spécialités, types de professionnels (lecture publique, gestion SUPER) |
| `professional`, `directory` | Profils, justificatifs, disponibilités, validation par l'admin, recherche avancée |
| `patient`, `medicalrecord`, `prescription` | Comptes patients, consentement, dossier et carnet de santé, ordonnances |
| `appointment`, `teleconsultation` | Rendez-vous, créneaux, salle vidéo (stub), messagerie, documents |
| `sos`, `pharmacy` | Alertes géolocalisées, structures de santé, pharmacies, commandes, livraison |
| `payment`, `subscription` | Paiements, séquestre, commissions, portefeuilles, retraits, formules d'abonnement |
| `review`, `complaint`, `partner`, `notification` | Avis, réclamations, partenaires et assurances, notifications |
| `admin` | Journal d'audit (écriture et consultation) |
| `shared` | Sécurité, erreurs (RFC 7807), pagination, chiffrement, géolocalisation, stockage |

Les modules communiquent par **identifiants** et **événements** (ex. `AppointmentEvents`), pas par entités partagées : la téléconsultation ouvre sa salle en réagissant à l'événement « rendez-vous démarré » sans que le module rendez-vous la connaisse.

### Ports et implémentations par défaut
Toute intégration externe est derrière une interface ; seule l'implémentation « stub » existe. **Aucune intégration réelle n'a été branchée.**

| Port | Rôle | Implémentation actuelle |
|---|---|---|
| `PaymentProvider` (stratégie) | Mobile Money (plusieurs opérateurs), carte, virement | stubs |
| `VideoProvider` | Salle de téléconsultation | stub |
| `EmailSender`, `NotificationChannel` | E-mail, SMS, push | stubs qui journalisent |
| `GeolocationProvider` | Distances, géocodage inverse | Haversine locale, pas de géocodage |
| `DeliveryProvider` | Livraison des commandes pharmacie | stub |
| `StorageService` | Fichiers (justificatifs, documents) | disque local (`APP_STORAGE_PATH`) |

### Tâches planifiées
- Rappels de rendez-vous : toutes les 5 min (`AppointmentReminderJob`), une seule fois par rendez-vous.
- Expiration des abonnements : toutes les heures (`SubscriptionExpiryJob`).
Elles s'exécutent dans chaque instance : en multi-instances, prévoir un verrou (ex. ShedLock) — voir `docs/EXPLOITATION.md`.

### Conventions transverses
- Identifiants UUID (entiers pour les référentiels), audit `createdAt/updatedAt/createdBy`, version optimiste, suppression logique là où c'est pertinent.
- Erreurs : `ProblemDetail` (RFC 7807) avec un champ `code` stable (`ApiException`). Pagination : `page`, `size` (max 100) → `{content, totalElements, number, size}`. Tri limité à une liste blanche de champs.
- Filtrage par `Specification` JPA ; les recherches « le plus proche » passent par `NearestSearch` (rectangle de 15, 50 puis 150 km, résultat exact).
- Horloge injectée (`Clock`) : les tests d'intégration la déplacent.

## Base de données
Toute évolution passe par une migration Flyway ; Hibernate est en `ddl-auto=validate`.

| Migration | Contenu |
|---|---|
| V1 | Utilisateurs, refresh tokens, réinitialisation, codes de secours 2FA |
| V2 | Journal d'audit en ajout seul (trigger qui interdit UPDATE/DELETE) |
| V3 | Référentiels (types, spécialités, villes, quartiers) et jeu de départ |
| V4 | Professionnels, justificatifs, zones, disponibilités et absences |
| V5 | Patients |
| V6 | Rendez-vous (contrainte d'exclusion `btree_gist` contre la double réservation) |
| V7 | Téléconsultation, messagerie, documents |
| V8 | Consentement versionné, dossier médical, journal d'accès (ajout seul) |
| V9 | Ordonnances |
| V10 | SOS, structures de santé, accès d'urgence |
| V11 | Pharmacies, membres, commandes, livraison |
| V12 | Paiements, portefeuilles (grand livre en ajout seul), retraits, abonnements |
| V13 | Notifications, avis, réclamations, partenaires, polices d'assurance |
| V14 | Index de performance : disponibilités, géographie, trigrammes (`pg_trgm`) |

Seeds de référence : types de professionnels, spécialités, villes de Côte d'Ivoire et quartiers d'Abidjan, formules d'abonnement, compte admin initial (créé au démarrage depuis les variables d'environnement, jamais en dur).

## Sécurité et données de santé
- **Rôles** : `PATIENT`, `PROFESSIONAL`, `ADMIN` ; niveaux d'admin `SUPER`, `SUPPORT`, `FINANCE` portés par des autorités `LEVEL_*`. Autorisation par méthode (`@PreAuthorize`) et règles de propriété (un patient ne voit que ses données).
- **Authentification** : JWT HS256 (accès 15 min), refresh en cookie httpOnly (web) ou en corps (`X-Client-Type: mobile`), rotation et détection de réutilisation, verrouillage après 5 échecs, limitation de débit sur `/auth/**` (en mémoire, par IP).
- **2FA** : TOTP RFC 6238 (secret chiffré, codes de secours hachés). Obligatoire pour les admins SUPER et FINANCE : le jeton porte l'état `mfa`, et tant qu'elle manque seul `/api/v1/auth/**` répond (`403 MFA_ENROLLMENT_REQUIRED`).
- **Chiffrement au repos** (AES-256-GCM, `EncryptedStringConverter`) : champs médicaux sensibles, messages, numéros de police. Les **fichiers** ne le sont pas encore.
- **Données de santé** : accès = propriétaire du compte, ou professionnel avec lien de soin **et** consentement actif. Les administrateurs n'y accèdent jamais. Chaque accès (refus compris) est journalisé en ajout seul ; seul le niveau SUPER lit ce journal.
- **Droits d'administration** :

| Écran / action | SUPER | SUPPORT | FINANCE |
|---|:-:|:-:|:-:|
| Tableau de bord, rendez-vous, SOS (suivi), abonnements (lecture) | ✓ | ✓ | ✓ |
| Utilisateurs : suspendre/réactiver, envoyer un lien de réinitialisation | ✓ | ✓ | — |
| Professionnels (validation), pharmacies, structures de santé, partenaires | ✓ | ✓ | — |
| Réclamations, avis (modération), annulation d'un SOS | ✓ | ✓ | — |
| Paiements, retraits, remboursements, formules d'abonnement | ✓ | — | ✓ |
| Référentiels, journal d'audit, journal d'accès médical | ✓ | — | — |

## Administration Angular
Structure : `core/` (auth, intercepteurs, gardes, modèles), `layout/` (coque avec menu latéral), `shared/ui/` (composants du design system), `features/<écran>/` (un dossier par écran : service, composant, tests).
- Jeton d'accès en mémoire uniquement ; intercepteur qui rejoue la requête après un refresh unique partagé ; erreurs API converties en notifications (`ProblemDetail`).
- Gardes : `authGuard`, `roleGuard('ADMIN')`, `levelGuard(...)`, `twoFactorEnrolledGuard` (redirige vers l'enrôlement 2FA).
- Tableaux paginés, triables et filtrés **côté serveur** ; formulaires réactifs ; français par défaut.
- Design system, composants et pièges : `docs/design-system.md`.

## Décisions de conception
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