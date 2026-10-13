# Contrat d'API – Phase 5 (SOS, pharmacie, livraison)

Base : `/api/v1`. Jeton d'accès requis. Erreurs : RFC 7807 + `code`. Pagination : `page`, `size` (max 100).

## SOS
| Méthode | Chemin | Rôle | Notes |
|---|---|---|---|
| POST | `/sos` | PATIENT | Crée une alerte (catégorie, position, description, personne à charge optionnelle). Réponse : alerte + structures de santé les plus proches (`app.sos.nearest-facilities`). Une seule alerte active par personne (409). |
| GET | `/sos/mine` | PATIENT | Mes alertes. |
| GET | `/sos/open` | PROFESSIONAL | Alertes ouvertes de ma ville. |
| GET | `/sos/{id}` | PATIENT (auteur), PROFESSIONAL (preneur), ADMIN | Détail ; autre → 404. |
| POST | `/sos/{id}/accept` | PROFESSIONAL | Prise en charge atomique : le premier gagne, les suivants 409 `SOS_ALREADY_TAKEN`. Crée une visite à domicile urgente (409 `PROFESSIONAL_BUSY` si chevauchement, la prise est alors annulée). |
| POST | `/sos/{id}/emergency-access` `{reason}` | PROFESSIONAL (preneur) | **Bris de glace** : accès au dossier de la personne sans consentement pendant `app.sos.emergency-access-hours` (24 h). Motif obligatoire ; chaque accès est journalisé `EMERGENCY_ACCESS`. |
| POST | `/sos/{id}/resolve` | PROFESSIONAL (preneur) | Clôture. |
| POST | `/sos/{id}/cancel` | PATIENT (auteur) | Annulation. |
| GET | `/health-facilities/nearby?lat&lng` | authentifié | Structures proches (Haversine). |
| GET | `/admin/sos`, `/admin/sos/summary` | ADMIN | Suivi (sans données de santé). |
| POST | `/admin/sos/{id}/cancel` `{reason}` | ADMIN SUPER/SUPPORT | Annulation administrative. |
| GET/POST/PUT | `/admin/health-facilities[/{id}]`, POST `/{id}/activate|deactivate` | ADMIN SUPER/SUPPORT | Gestion des structures (saisies par l'admin, rien de pré-rempli). |

## Pharmacie
| Méthode | Chemin | Rôle | Notes |
|---|---|---|---|
| GET | `/pharmacies`, `/pharmacies/nearby`, `/pharmacies/{id}` | authentifié | Recherche (ville, nom, garde…) des pharmacies actives. |
| POST | `/pharmacy-orders` | PATIENT | Transmet une ordonnance valide à une pharmacie (retrait `PICKUP` ou `DELIVERY` + adresse). Numéro `CMD-AAAA-NNNNNN`. 409 `PRESCRIPTION_NOT_USABLE` / commande déjà active. |
| GET | `/pharmacy-orders`, `/pharmacy-orders/{id}` | PATIENT | Mes commandes ; code de remise visible seulement en `OUT_FOR_DELIVERY`. |
| POST | `/pharmacy-orders/{id}/cancel` | PATIENT | Tant que non prête. |
| GET | `/pharmacy/mine` | PROFESSIONAL (PHARMACIST) | Mes pharmacies. |
| GET | `/pharmacy/orders`, `/pharmacy/orders/{id}` | PROFESSIONAL membre | Lecture journalisée `VIEW_PHARMACY_ORDER`. |
| POST | `/pharmacy/orders/{id}/accept` (disponibilité par ligne) / `reject` `{reason}` / `ready` | PROFESSIONAL membre | |
| POST | `/pharmacy/orders/{id}/handover` | PROFESSIONAL membre | Retrait au comptoir → `COMPLETED`. |
| POST | `/pharmacy/orders/{id}/dispatch` | PROFESSIONAL membre | Lance la livraison via `DeliveryProvider` (stub) → `OUT_FOR_DELIVERY`, code à 6 chiffres. |
| POST | `/pharmacy/orders/{id}/deliver` `{code}` | PROFESSIONAL membre | Valide le code ; 5 erreurs max (409 `DELIVERY_CODE_LOCKED`), compteur persisté. |
| POST | `/pharmacy/orders/{id}/delivery-failed` `{reason}` | PROFESSIONAL membre | |
| CRUD | `/admin/pharmacies[/{id}]`, activate/deactivate, `/{id}/members/{userId}` (PUT/DELETE) | ADMIN SUPER/SUPPORT | Un membre doit être un professionnel actif de type `PHARMACIST`. |

Statuts : `SENT → ACCEPTED → READY → COMPLETED` (retrait) ou `READY → OUT_FOR_DELIVERY → DELIVERED | DELIVERY_FAILED` ; `REJECTED`, `CANCELLED`. Transition illégale → 409 `INVALID_STATE`.
Événements publiés (consommés en Phase 7) : `SosEvents`, `PharmacyEvents`.
