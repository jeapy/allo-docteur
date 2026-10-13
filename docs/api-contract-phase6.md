# Contrat d'API – Phase 6 (paiements, portefeuille, commissions, abonnements)

Base : `/api/v1`. Jeton d'accès requis. Erreurs : RFC 7807 + `code`. Montants en XOF, sans centimes. Pagination : `page`, `size` (max 100).

## Principes
- **Paiement à la réservation, fonds en séquestre.** Le patient paie un rendez-vous (ou une commande de pharmacie) ; les fonds restent `CAPTURED` jusqu'à la prestation, puis sont répartis (`RELEASED`) : net au portefeuille du professionnel (ou de la pharmacie), commission à celui de la plateforme. Annulation ou refus → `REFUNDED`.
- Un rendez-vous payant ne peut être **confirmé** par le professionnel qu'une fois payé (409 `PAYMENT_REQUIRED`, désactivable : `app.payments.require-payment-to-confirm`). Les visites urgentes SOS (confirmées d'office) se paient après coup.
- **Commission** : taux global `app.payments.commission-rate` (10 % par défaut), remplacé par le taux du plan d'abonnement en cours du professionnel s'il en définit un (PRO_PREMIUM : 5 %). Le taux et le montant sont figés sur le paiement. Pas de commission sur pharmacie (`pharmacy-commission-rate` = 0), abonnements et recharges.
- Cycle d'un paiement : `PENDING → CAPTURED → RELEASED | REFUNDED`, ou `PENDING → FAILED`. Un seul paiement « vivant » par objet (index unique) : pas de double paiement.
- Opérateurs derrière le port `PaymentProvider` (stub par défaut `FAKE_MOBILE_MONEY`, `FAKE_CARD`, `FAKE_BANK`). Stub : une référence payeur finissant par `0000` est refusée, par `9999` reste en attente (résolue par `refresh`) ; un virement reste en attente jusqu'à confirmation de la finance.
- `Idempotency-Key` (en-tête, optionnel) sur `POST /payments`, `/wallet/topups`, `/subscriptions` : rejouer la même clé renvoie le même paiement.
- Un refus de l'opérateur n'est pas une erreur HTTP : le paiement est renvoyé avec `status = FAILED` et `failureReason`.

## Paiements (PATIENT ; lecture aussi pour le PROFESSIONAL bénéficiaire)
| Méthode | Chemin | Notes |
|---|---|---|
| POST | `/payments` `{payableType: APPOINTMENT\|PHARMACY_ORDER, payableId, method, provider?, payerReference?}` | `payerReference` : numéro Mobile Money (`+22507…`) ou jeton de carte ; rien pour un virement ou le portefeuille. 404 si l'objet n'est pas au payeur ; 409 `ALREADY_PAID`, `PAYMENT_PENDING`, `NOT_PAYABLE`, `NOTHING_TO_PAY`, `INSUFFICIENT_FUNDS` (portefeuille). |
| GET | `/payments` | Mes paiements (payés, ou reçus pour un professionnel) ; filtre `status`. |
| GET | `/payments/{id}` | Payeur ou bénéficiaire ; sinon 404. |
| POST | `/payments/{id}/refresh` | Interroge l'opérateur pour un paiement `PENDING`. |

Rendez-vous : payable de `REQUESTED` à `COMPLETED` ; montant = tarif figé à la réservation. Commande de pharmacie : payable une fois acceptée (devis connu) ; montant = devis + frais de livraison si livraison. Refus, annulation ou échec de livraison → remboursement automatique ; fin de prestation (`COMPLETED`, `NO_SHOW`, `DELIVERED`) → répartition.

## Portefeuille (PATIENT, PROFESSIONAL)
| Méthode | Chemin | Notes |
|---|---|---|
| GET | `/wallet` | `{balance, currency}`. |
| GET | `/wallet/transactions` | Grand livre, plus récent d'abord : `type CREDIT/DEBIT`, `reason`, `amount`, `balanceAfter`. En ajout seul (trigger PostgreSQL). |
| POST | `/wallet/topups` `{amount, method: MOBILE_MONEY\|CARD\|BANK_TRANSFER, payerReference?}` | Montant entre `min-topup` et `max-topup`. Crédité à l'encaissement. |
| POST | `/wallet/withdrawals` `{amount, method: MOBILE_MONEY\|BANK_TRANSFER, destination}` | PROFESSIONAL. Le montant est débité dès la demande. Minimum `min-withdrawal`. Destination chiffrée au repos, masquée dans les réponses. |
| GET | `/wallet/withdrawals` | Mes demandes. |

## Abonnements (PATIENT, PROFESSIONAL)
| Méthode | Chemin | Notes |
|---|---|---|
| GET | `/subscriptions/plans` | Formules de mon profil (PRO_STANDARD, PRO_PREMIUM / PATIENT_PREMIUM). |
| GET | `/subscriptions/current` | 204 si aucun. |
| GET | `/subscriptions/history` | Paginé. |
| POST | `/subscriptions/trial` | PROFESSIONAL, une seule fois : 30 jours de PRO_STANDARD. |
| POST | `/subscriptions` `{planCode, method, payerReference?}` | Renvoie le paiement ; l'abonnement s'active à l'encaissement. Même plan : la période s'ajoute à la fin de l'abonnement en cours ; autre plan : l'ancien est clos, le nouveau démarre aussitôt (sans prorata). |
| POST | `/subscriptions/cancel` | Résiliation immédiate, sans remboursement. |

Les abonnements sont **enregistrés et facturés sans blocage métier** (décision de la phase 6) ; seul effet : le taux de commission du plan PRO.

## Administration
Finance = niveaux `SUPER` et `FINANCE`.
| Méthode | Chemin | Niveau |
|---|---|---|
| GET | `/admin/payments` (`payerId, status, method, type, from, to`) | FINANCE/SUPER |
| GET | `/admin/payments/summary?from=` | FINANCE/SUPER : volume, commissions, revenus d'abonnement, remboursé, séquestre, attentes, solde plateforme. |
| GET | `/admin/payments/{id}` | FINANCE/SUPER |
| POST | `/admin/payments/{id}/confirm-transfer`, `/reject-transfer {reason}` | FINANCE/SUPER : virements en attente. |
| POST | `/admin/payments/{id}/refund {reason}` | FINANCE/SUPER : seulement tant que `CAPTURED` (409 `NOT_REFUNDABLE`). |
| GET | `/admin/withdrawals?status=` | FINANCE/SUPER (destination complète). |
| POST | `/admin/withdrawals/{id}/pay`, `/reject {reason}` | FINANCE/SUPER : versement via le port (409 `PAYOUT_FAILED`, rien n'est modifié) ou rejet (montant restitué). |
| GET | `/admin/subscriptions?status=`, `/admin/subscription-plans` | tout administrateur |
| PUT | `/admin/subscription-plans/{id}` | FINANCE/SUPER : prix, durée, commission, activation. |

Événements publiés : `PaymentEvents.Captured/Released/Refunded/Failed`, `SubscriptionEvents.Activated` (notifications en phase 7).
