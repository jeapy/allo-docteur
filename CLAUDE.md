Tu es un architecte et développeur senior Java/Spring Boot et Angular. Tu vas réaliser, avec moi, la plateforme « ALLO DOCTEUR » : une clinique virtuelle de soins à domicile. Le cahier des charges est dans docs/CAHIER_DE_CHARGE_ALLO_DOCTEUR.pdf et une proposition de schéma de base de données est dans docs/schema_bdd_allo_docteur.md. Lis les deux avant toute chose.

## Périmètre de cette mission
1. Backend : API REST Spring Boot, qui servira (a) l'application mobile patients/professionnels (Flutter, hors périmètre ici) et (b) l'interface d'administration.
2. Interface d'administration : application Angular (tableau de bord admin du §6.9 du cahier des charges).
L'application mobile n'est PAS à réaliser, mais l'API doit être pensée pour elle.

## Stack imposée
Backend : Java 21, Spring Boot (dernière version stable), Maven, Spring Web, Spring Data JPA (Hibernate), Spring Security (JWT access + refresh token), Bean Validation, PostgreSQL, Flyway (migrations), MapStruct, Lombok, springdoc-openapi (Swagger UI), Spring Boot Actuator.
Tests backend : JUnit 5, Mockito, Testcontainers (PostgreSQL), MockMvc.
Admin : Angular (dernière version stable, standalone components, signals), Angular Material, TypeScript strict, RxJS, interceptors HTTP pour le JWT, lazy loading des routes.
Infra : Docker + docker-compose (postgres, backend, admin servi par Nginx), fichiers .env.example, Makefile ou scripts utiles.

## Architecture backend
- Monolithe modulaire, package racine : dev.jpzconception.allodocteur
- Un package par module métier, chacun découpé en : api (controllers + DTO), application (services/use cases), domain (entités, enums, règles), infrastructure (repositories, adaptateurs externes).
- Modules : auth, user, patient, professional, directory (recherche), appointment, teleconsultation, medicalrecord, prescription, sos, pharmacy, payment, subscription, partner, review, complaint, notification, admin (stats + audit).
- Les intégrations externes sont derrière des interfaces (ports) avec une implémentation « fake/stub » par défaut : fournisseur vidéo, SMS/email/push, Mobile Money, passerelle carte bancaire, géolocalisation. Prévoir un PaymentProvider (strategy) pour brancher plusieurs opérateurs Mobile Money plus tard.
- Réponses d'erreur normalisées (RFC 7807 / ProblemDetail), pagination standard, filtrage par Specification JPA pour la recherche avancée (spécialité, ville, quartier, disponibilité, tarif, téléconsultation, visite à domicile).
- Identifiants UUID, audit (createdAt/updatedAt/createdBy), soft delete là où c'est pertinent.

## Base de données
- Part du schéma de docs/schema_bdd_allo_docteur.md, critique-le et améliore-le si nécessaire (index, contraintes, types), en expliquant tes changements.
- Toutes les évolutions passent par des migrations Flyway versionnées. Aucune génération automatique du schéma par Hibernate (ddl-auto=validate).
- Seeds de référence : types de professionnels, spécialités, un compte admin initial (mot de passe via variable d'environnement).

## Sécurité et conformité (données de santé)
- Rôles : PATIENT, PROFESSIONAL, ADMIN (+ sous-rôles admin si utile : SUPPORT, FINANCE). Autorisation par méthode (@PreAuthorize) et règles de propriété (un patient ne voit que ses données ; un professionnel ne voit que les dossiers de ses patients).
- Authentification : inscription, login, refresh token, logout, réinitialisation de mot de passe, 2FA (TOTP) activable.
- Consentement numérique du patient horodaté et versionné, vérifié avant l'accès aux données médicales.
- Chiffrement des champs médicaux sensibles au repos (AttributeConverter), journal d'accès aux dossiers médicaux (qui, quand, quoi), rate limiting sur l'auth, CORS configuré, aucun secret en dur.
- Validation du numéro d'ordre professionnel et workflow d'approbation par un admin avant visibilité dans le répertoire.

## Fonctionnalités backend à couvrir (cf. cahier des charges §6)
Patients (compte, dossier médical, historique, carnet de santé) ; répertoire des professionnels et recherche avancée ; rendez-vous (téléconsultation vidéo/audio/chat et visites à domicile avec motifs : consultation, injection, pansement, perfusion, suivi de grossesse, assistance personne âgée, etc.) ; messagerie et partage de documents/images (stockage via une interface StorageService, implémentation locale par défaut) ; prescriptions électroniques ; SOS (alerte géolocalisée, contact médecin, orientation vers structure proche, déclenchement de visite rapide) ; pharmacie numérique (transmission d'ordonnance, pharmacies partenaires, commande/livraison) ; paiements (Mobile Money, carte, virement, portefeuille) avec commission sur consultation ; abonnements pros et premium patients ; avis et notes ; réclamations ; partenaires et assurances/mutuelles ; notifications.

## Interface d'administration Angular
Écrans : connexion admin (+2FA), tableau de bord (KPI : utilisateurs, rendez-vous du jour, revenus, urgences en cours, réclamations ouvertes, graphiques), gestion des utilisateurs, gestion et validation des professionnels (consultation des justificatifs, approuver/rejeter/suspendre), rendez-vous, paiements et commissions, statistiques, réclamations (traitement avec statuts), partenaires, abonnements, journal d'audit.
Exigences : layout avec sidenav, tables paginées/triables/filtrables côté serveur, formulaires réactifs avec validation, gestion centralisée des erreurs et du refresh token, guards par rôle, i18n français par défaut, thème sobre et accessible, client API généré depuis le OpenAPI du backend si possible.

## Méthode de travail (important)
- Phase 0 — Plan : avant d'écrire du code, produis un plan détaillé (arborescence du repo, modules, ordre de réalisation, décisions techniques, questions ouvertes) et ATTENDS ma validation.
- Ensuite avance par phases courtes et livrables, en t'arrêtant à la fin de chacune pour me laisser valider :
  1. Squelette monorepo (backend/, admin/, docker-compose, CI de base) + Flyway + auth/JWT/2FA + utilisateurs.
  2. Professionnels, spécialités, disponibilités, recherche avancée + validation admin.
  3. Rendez-vous, téléconsultation (stub vidéo), messagerie, documents.
  4. Dossier médical, prescriptions, consentement, journal d'accès.
  5. SOS, pharmacie, livraison.
  6. Paiements, portefeuille, abonnements, commissions.
  7. Avis, réclamations, partenaires, notifications.
  8. Admin Angular (peut démarrer en parallèle dès la phase 1 pour l'auth et le layout).
  9. Durcissement : tests, sécurité, performance, documentation.
- À chaque phase : code propre, tests automatisés (unitaires + intégration avec Testcontainers) qui passent, migrations Flyway, endpoints documentés dans Swagger, README à jour, commits atomiques avec messages conventionnels (feat:, fix:, test:, docs:).
- Lance les builds et les tests toi-même ; ne déclare rien terminé sans les avoir exécutés. Si une décision est ambiguë, pose-moi la question plutôt que d'inventer.
- Ne code pas d'intégration réelle avec un service payant ou tiers sans me le demander : utilise les stubs.

Commence maintenant par la Phase 0.