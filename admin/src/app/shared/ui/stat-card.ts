import { Component, input } from '@angular/core';
import { NgIcon } from '@ng-icons/core';
import { UiCard } from './card';
import { UiSkeleton } from './skeleton';

/** Carte KPI : libellé, valeur, icône, indication. Affiche un squelette pendant le chargement. */
@Component({
  selector: 'ui-stat-card',
  imports: [NgIcon, UiCard, UiSkeleton],
  template: `
    <ui-card [interactive]="true" class="h-full" [class.ring-2]="alert()" [class.ring-warning]="alert()">
      <div class="flex items-start justify-between gap-3">
        <div class="min-w-0">
          <p class="m-0 text-sm font-medium text-muted">{{ label() }}</p>
          @if (loading()) {
            <ui-skeleton class="mt-3 h-8 w-24" />
          } @else {
            <p class="m-0 mt-2 text-2xl font-semibold [overflow-wrap:anywhere] tracking-tight text-text">{{ value() ?? '—' }}</p>
          }
          @if (hint() && !loading()) { <p class="m-0 mt-1 text-xs text-muted">{{ hint() }}</p> }
        </div>
        <span class="grid h-10 w-10 shrink-0 place-items-center rounded-md bg-primary-soft text-primary">
          <ng-icon [name]="icon()" size="20" aria-hidden="true" />
        </span>
      </div>
    </ui-card>
  `,
  host: { class: 'block', role: 'group', '[attr.aria-busy]': 'loading()' },
})
export class UiStatCard {
  readonly label = input.required<string>();
  readonly value = input<string | number | null>('—');
  readonly icon = input('lucideActivity');
  readonly hint = input<string | null>();
  readonly loading = input(false);
  /** Met la carte en évidence (anneau) : le sens est aussi porté par le texte, pas seulement la couleur. */
  readonly alert = input(false);
}
