# Design system – Administration Allo Docteur

Thème **clair** uniquement. Stack : Tailwind CSS 4 + primitives headless Spartan (`@spartan-ng/brain`) + icônes lucide (`@ng-icons`). Angular Material a été **retiré** (migration terminée, voir §5).

## 1. Principes
Minimalisme et espace blanc · coins 8 à 16 px · ombres douces · hiérarchie par la taille et le poids, pas par la couleur seule · transitions de 150 à 250 ms · contrastes WCAG AA vérifiés · mobile d'abord.

## 2. Tokens (`admin/src/styles.scss`, variables `--ds-*`)
| Famille | Tokens | Valeurs |
|---|---|---|
| Marque | `primary` / `primary-hover` / `primary-soft` | `#0369a1` (bleu hospitalier) / `#075985` / `#e0f0fa` (5,9:1 sur blanc) |
| Secondaire | `secondary` / `secondary-soft` | `#0f766e` (sarcelle) / `#e6f4f2` |
| Neutres | `bg`, `surface`, `surface-muted`, `border`, `border-strong`, `text`, `text-muted` | `#f8fafc`, `#fff`, `#f1f5f9`, `#e2e8f0`, `#64748b` (4,8:1), `#0f172a` (17:1), `#475569` (7,6:1) |
| Succès | `success` / `success-soft` | `#166534` / `#dcfce7` (6,5:1) |
| Alerte | `warning` / `warning-soft` | `#92400e` / `#fef3c7` (6,4:1) |
| Erreur | `danger`, `danger-text` / `danger-soft` | `#b91c1c`, `#991b1b` / `#fee2e2` |
| Info | `info` / `info-soft` | `#1e40af` / `#dbeafe` |
| Espacement | `space-1..5` | 4 · 8 · 16 · 24 · 32 px |
| Rayons | `radius-sm/md/lg` | 8 · 12 · 16 px |
| Ombres | `shadow-sm/md/lg` | douces, deux couches |
| Mouvement | `duration-fast/base/slow`, `ease` | 150 · 200 · 250 ms, `cubic-bezier(.2,0,0,1)` |
| Typographie | `font` | Inter Variable (auto-hébergée via `@fontsource-variable/inter`) |

`src/tailwind.css` expose ces tokens à Tailwind (`bg-primary`, `text-muted`, `rounded-lg`, `shadow-md`, `ease-ds`…). Le preflight Tailwind n'est volontairement **pas** importé : `styles.scss` pose le reset minimal (box-sizing, marges, police). Les couleurs ne sont jamais seules à porter un sens (texte dans les badges, libellés dans les graphiques).

## 3. Composants (`admin/src/app/shared/ui`)
| Composant | Usage |
|---|---|
| `uiBtn` | `<button uiBtn variant="primary|secondary|outline|ghost|danger" size="sm|md|lg|icon">` |
| `ui-badge` | `<ui-badge tone="success|warning|danger|info|primary|neutral">` |
| `ui-card` | surface arrondie ; `[padded]`, `[interactive]` |
| `ui-skeleton` | chargement ; `class="h-6 w-24"`, `aria-hidden` |
| `ui-empty-state` | état vide : titre, description, icône, action projetée |
| `ui-page-header` | titre, sous-titre, actions |
| `ui-stat-card` | KPI avec squelette intégré |
| `ui-field` + `uiInput` | champ à libellé flottant, erreur reliée (`role="alert"`), bordure rouge si invalide **et** touché |
| `table[uiTable]` + `.ui-table-wrap` | tableau natif stylé (survol, en-têtes, colonnes `.num`) |
| `ui-select-field` + `uiSelect` | liste déroulante native stylée, libellé toujours en haut |
| `uiCheckbox` | case à cocher native colorée par le thème |
| `th[uiSortHeader]` | en-tête triable côté serveur (`champ,asc|desc`), émet `{active, direction}`, `aria-sort` |
| `ui-tabs` | onglets accessibles (tablist, flèches) ; le contenu est géré par le parent |
| `ui-table-skeleton` + `.ui-table-card` | squelette de premier chargement ; cadre tableau + pagination ; `.ui-filters`, `.ui-row-actions` |
| `statusTone()` | associe un statut métier à la couleur d'un `ui-badge` |
| `ui-pagination` | pagination serveur, émet `{pageIndex, pageSize}` (même forme que `PageEvent`) |
| `ui-bar-chart` | histogramme sans dépendance, tableau équivalent pour lecteurs d'écran |
| `ToastService` | remplace `MatSnackBar` (ngx-sonner, conteneur dans `App`) |
| `UiDialog` | modales sur le dialogue headless de Spartan (focus piégé, Échap, ARIA) : `dialog.open(Composant, data)` rend un `Observable` du résultat |

Layout (`layout/shell`) : barre latérale rétractable (icônes seules sur bureau, mémorisée ; tiroir sur mobile), en-tête avec recherche, notifications et menu profil, lien « Aller au contenu », `aria-current` sur l'entrée active. Transitions entre pages : View Transitions API du routeur (fondu de 150/250 ms, désactivé si `prefers-reduced-motion`).

## 4. Dépendances ajoutées
`tailwindcss`, `@tailwindcss/postcss`, `postcss`, `tw-animate-css`, `clsx`, `tailwind-merge`, `class-variance-authority`, `@spartan-ng/brain`, `@ng-icons/core`, `@ng-icons/lucide`, `ngx-sonner`, `@fontsource-variable/inter` ; en dev : `@spartan-ng/cli` (non utilisé pour générer du code, voir §6). Configuration : `admin/.postcssrc.json`, `src/tailwind.css`, `angular.json` (second fichier de styles, budget de style de composant relevé à 6 ko).

## 5. Plan de migration (écran par écran, sans casser la logique)
Règle : on ne touche ni aux services, ni aux modèles, ni aux composants de logique ; on remplace seulement le gabarit (`.html`), le style et les `imports` Material.

| Lot | Écrans | État |
|---|---|---|
| 0 | Tokens, kit `shared/ui`, layout, connexion, dashboard | **fait** |
| 1 | Listes simples : utilisateurs, rendez-vous, abonnements, avis | **fait** |
| 2 | Listes à actions et modales : professionnels (+ détail), réclamations (+ détail), partenaires, pharmacies | **fait** |
| 3 | Finance, SOS, journal d'accès, `ReasonDialog` et formulaires en modale (`UiDialog`) | **fait** |
| 4 | Retrait d'Angular Material : `MatSnackBar → ToastService`, `MatDialog → UiDialog`, `MatPaginator → ui-pagination`, `MatTable → table[uiTable]`, `MatSelect → select natif stylé` ; suppression du thème `mat.theme` et de `material-icons` | **fait** (`@angular/material` et `material-icons` désinstallés ; `@angular/cdk` conservé : overlay et layout) |

Correspondances : `mat-form-field + matInput → ui-field + uiInput` · `mat-flat-button → uiBtn` · `mat-stroked-button → uiBtn variant="outline"` · `mat-button → uiBtn variant="ghost"` · `mat-progress-bar → ui-skeleton` · `mat-tab-group → ui-tabs` · `mat-select → select[uiSelect]` · `mat-checkbox → input[uiCheckbox]` · `matSort → th[uiSortHeader]` · statut texte → `ui-badge`.

## 6. Décisions et limites
- Le générateur interactif `@spartan-ng/cli` n'a pas pu être piloté en environnement non interactif : la couche « helm » est écrite à la main dans `shared/ui` au-dessus des primitives `brain` (dialogue, et plus tard onglets, sélecteur, tooltip).
- Recherche et cloche de l'en-tête : **visuelles seulement**. Brancher la recherche globale et les notifications admin demandera de nouveaux endpoints (hors périmètre « ne pas toucher aux services »).
- Mode sombre : hors périmètre (thème clair demandé).
- Les graphiques sont des barres horizontales maison (pas de bibliothèque) ; une courbe temporelle nécessiterait un endpoint de séries.
- Le dashboard compose les services existants et masque sans bruit les blocs interdits au niveau d'admin (ex. finance pour SUPPORT).

## 6. Points d'attention rencontrés à la migration
- Les styles de CDK Overlay (`.cdk-overlay-pane { max-width: 100% }`) ne sont pas dans une couche CSS et l'emportent sur les utilitaires Tailwind : les modales fixent leur largeur avec `w-[min(32rem,calc(100vw-2rem))]` et `max-h-…!`.
- Les classes passées à `BrnDialogService` (`panelClass`, `backdropClass`) doivent être un tableau de jetons, pas une chaîne contenant des espaces.
- `.sr-only` est en `position: absolute` : le cadre du tableau est en `position: relative`, sinon ces éléments débordent hors du conteneur défilant et créent un défilement horizontal de la page sur mobile.
- Les pipes `currency`/`date` appelés avec la locale `'fr'` exigent `registerLocaleData(localeFr)` (fait dans `app.config.ts`) ; sans cela, NG0701 interrompait le rendu des écrans rendez-vous et pharmacies.
