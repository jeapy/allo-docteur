import { Component, computed, input } from '@angular/core';
import { cn } from './cn';

/** Squelette de chargement : `<ui-skeleton class="h-6 w-24" />`. Masqué aux lecteurs d'écran. */
@Component({
  selector: 'ui-skeleton',
  template: '',
  host: { '[class]': 'classes()', 'aria-hidden': 'true' },
})
export class UiSkeleton {
  readonly rounded = input<'sm' | 'md' | 'full'>('sm');
  protected readonly classes = computed(() =>
    cn('block animate-pulse bg-surface-muted', { sm: 'rounded-sm', md: 'rounded-md', full: 'rounded-full' }[this.rounded()]),
  );
}
