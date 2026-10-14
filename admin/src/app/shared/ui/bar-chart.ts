import { DecimalPipe } from '@angular/common';
import { Component, computed, input } from '@angular/core';
import { TranslocoPipe } from '@jsverse/transloco';

export interface BarDatum {
  /** Libellé, ou clé i18n si `translate` est activé sur le graphique. */
  label: string;
  value: number;
  /** Variable CSS du design system, ex. `var(--ds-primary)`. */
  color?: string;
}

/**
 * Histogramme horizontal en SVG/CSS, sans dépendance. Accessible : tableau équivalent masqué visuellement
 * (`sr-only`) et barres décoratives (`aria-hidden`).
 */
@Component({
  selector: 'ui-bar-chart',
  imports: [DecimalPipe, TranslocoPipe],
  template: `
    <div class="flex flex-col gap-3" aria-hidden="true">
      @for (d of bars(); track d.label) {
        <div class="grid grid-cols-[minmax(6rem,9rem)_1fr_auto] items-center gap-3 text-sm">
          <span class="truncate text-muted">{{ translate() ? (d.label | transloco) : d.label }}</span>
          <span class="h-2.5 overflow-hidden rounded-full bg-surface-muted">
            <span class="block h-full rounded-full transition-[width] duration-500 ease-ds"
              [style.width.%]="d.pct" [style.background]="d.color"></span>
          </span>
          <span class="tabular-nums font-medium text-text">{{ d.value | number: '1.0-0' }}</span>
        </div>
      }
    </div>
    <table class="sr-only">
      <caption>{{ caption() }}</caption>
      <tbody>@for (d of bars(); track d.label) { <tr><th scope="row">{{ translate() ? (d.label | transloco) : d.label }}</th><td>{{ d.value }}</td></tr> }</tbody>
    </table>
  `,
})
export class UiBarChart {
  readonly data = input.required<BarDatum[]>();
  readonly caption = input('');
  /** Les libellés sont des clés i18n (Transloco). */
  readonly translate = input(false);

  protected readonly bars = computed(() => {
    const max = Math.max(1, ...this.data().map((d) => d.value));
    return this.data().map((d) => ({
      ...d,
      pct: Math.round((d.value / max) * 100),
      color: d.color ?? 'var(--ds-primary)',
    }));
  });
}
