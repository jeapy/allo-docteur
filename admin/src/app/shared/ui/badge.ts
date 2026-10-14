import { Component, computed, input } from '@angular/core';
import { cn } from './cn';

export type BadgeTone = 'neutral' | 'primary' | 'success' | 'warning' | 'danger' | 'info';

const TONES: Record<BadgeTone, string> = {
  neutral: 'bg-surface-muted text-muted',
  primary: 'bg-primary-soft text-primary-hover',
  success: 'bg-success-soft text-success',
  warning: 'bg-warning-soft text-warning',
  danger: 'bg-danger-soft text-danger-text',
  info: 'bg-info-soft text-info',
};

/** Pastille de statut. La couleur n'est jamais seule : le texte porte le sens. */
@Component({
  selector: 'ui-badge',
  template: '<ng-content />',
  host: { '[class]': 'classes()' },
})
export class UiBadge {
  readonly tone = input<BadgeTone>('neutral');
  protected readonly classes = computed(() =>
    cn('inline-flex items-center rounded-full px-2.5 py-0.5 text-xs font-medium leading-5', TONES[this.tone()]),
  );
}
