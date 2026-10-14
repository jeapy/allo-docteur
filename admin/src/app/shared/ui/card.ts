import { Component, computed, input } from '@angular/core';
import { cn } from './cn';

/** Carte : surface blanche, coins arrondis, ombre douce. Contenu libre. */
@Component({
  selector: 'ui-card',
  template: '<ng-content />',
  host: { '[class]': 'classes()' },
})
export class UiCard {
  readonly padded = input(true);
  readonly interactive = input(false);
  protected readonly classes = computed(() =>
    cn(
      'block rounded-lg border border-border bg-surface shadow-sm transition-shadow duration-200 ease-ds',
      this.padded() && 'p-6',
      this.interactive() && 'hover:shadow-md',
    ),
  );
}
