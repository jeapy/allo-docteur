import { Directive, computed, input } from '@angular/core';
import { cn } from './cn';

export type ButtonVariant = 'primary' | 'secondary' | 'outline' | 'ghost' | 'danger';
export type ButtonSize = 'sm' | 'md' | 'lg' | 'icon';

const BASE =
  'inline-flex items-center justify-center gap-2 rounded-sm border border-transparent font-medium whitespace-nowrap cursor-pointer select-none ' +
  'no-underline transition-all duration-150 ease-ds active:scale-[0.98] disabled:pointer-events-none disabled:opacity-50';

const VARIANTS: Record<ButtonVariant, string> = {
  primary: 'bg-primary text-on-primary shadow-sm hover:bg-primary-hover hover:shadow-md',
  secondary: 'bg-primary-soft text-primary-hover hover:bg-[#d3ebe7]',
  outline: 'border border-border-strong bg-surface text-text hover:bg-surface-muted',
  ghost: 'bg-transparent text-text hover:bg-surface-muted',
  danger: 'bg-danger text-on-primary shadow-sm hover:bg-[#991b1b]',
};

const SIZES: Record<ButtonSize, string> = {
  sm: 'h-8 px-3 text-sm',
  md: 'h-10 px-4 text-sm',
  lg: 'h-12 px-6 text-base',
  icon: 'h-10 w-10',
};

/** Bouton du design system : `<button uiBtn variant="outline" size="sm">`. */
@Directive({
  selector: 'button[uiBtn], a[uiBtn]',
  host: { '[class]': 'classes()' },
})
export class UiButton {
  readonly variant = input<ButtonVariant>('primary');
  readonly size = input<ButtonSize>('md');
  protected readonly classes = computed(() => cn(BASE, VARIANTS[this.variant()], SIZES[this.size()]));
}
