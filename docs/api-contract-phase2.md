# Contrat d'API – Phase 2 (professionnels, répertoire, validation)

Base : `/api/v1`. Toutes les routes exigent un jeton d'accès. Erreurs : RFC 7807 + `code`.

## Référentiels (lecture, tout utilisateur connecté)
| GET | `/reference/professional-types` · `/reference/specialties` · `/reference/cities` (avec quartiers) |

## Espace professionnel (rôle PROFESSIONAL)
| Méthode | Chemin | Notes |
|---|---|---|
| GET / PUT | `/professionals/me` | PUT = création ou mise à jour. Création → `PENDING`. Modifier le n° d'ordre ou le type d'un profil approuvé, ou corriger un profil rejeté, le repasse en `PENDING`. |
| GET | `/professionals/me/availability` | `{rules, exceptions}` (exceptions à venir) |
| PUT | `/professionals/me/availability/rules` | Remplace toutes les plages hebdomadaires ; `dayOfWeek` 1 (lundi) à 7 ; chevauchement refusé |
| POST / DELETE | `/professionals/me/availability/exceptions[/{id}]` | Absence ; sans heures = journée entière |
| GET / POST / DELETE | `/professionals/me/documents[/{id}]` | POST multipart `type` (LICENSE, DIPLOMA, ID_CARD, OTHER) + `file` ; PDF/JPEG/PNG, 5 Mo, 10 max ; type détecté par signature |
| GET | `/professionals/documents/{id}/content` | Propriétaire ou admin ; sinon 404 |

## Répertoire (tout utilisateur connecté)
`GET /professionals` — seuls les profils `APPROVED` dont le compte est actif.
Paramètres : `q, typeId, specialtyId (répétable), cityId, districtId, minFee, maxFee, teleconsultation, homeVisit, day (1-7), date (ISO), minRating, sort (rating|fee_asc|fee_desc|experience), page, size`.
- `cityId`/`districtId` : lieu d'exercice **ou** zone d'intervention.
- Le tarif filtré est celui de la visite à domicile si `homeVisit=true` sans `teleconsultation=true`, sinon celui de la téléconsultation.
- `date` prime sur `day` et exclut les professionnels absents toute la journée.

`GET /professionals/{id}` — fiche ; email, statut et justificatifs réservés au propriétaire et aux admins ; 404 si non approuvé.

## Administration
| Méthode | Chemin | Autorisation |
|---|---|---|
| GET | `/admin/professionals?status&typeId&q&page&size&sort=oldest` | admin |
| GET | `/admin/professionals/{id}` | admin |
| POST | `/admin/professionals/{id}/approve` | SUPER, SUPPORT — exige ≥ 1 justificatif |
| POST | `/admin/professionals/{id}/reject` `{reason}` | SUPER, SUPPORT — depuis `PENDING` |
| POST | `/admin/professionals/{id}/suspend` `{reason}` | SUPER, SUPPORT — depuis `APPROVED` |

Transitions : `PENDING → APPROVED | REJECTED` ; `APPROVED → SUSPENDED` ; `SUSPENDED → APPROVED` ; `REJECTED → PENDING` (correction par le professionnel). Toute autre transition : 409 `INVALID_STATE`.
