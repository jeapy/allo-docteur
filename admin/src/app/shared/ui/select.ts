import { Component, Directive, input } from '@angular/core';
import { NgIcon } from '@ng-icons/core';

/** `<select>` natif stylé (accessible au clavier et sur mobile). À placer dans `<ui-select-field>`. */
@Directive({
  selector: 'select[uiSelect]',
  host: {
    class:
      'block w-full appearance-none rounded-sm border border-border-strong bg-surface pl-3 pr-9 pt-5 pb-1.5 text-base text-text ' +
      'transition-colors duration-150 ease-ds hover:bg-surface-muted focus:outline-none focus:border-primary ' +
      'focus:shadow-[0_0_0_3px_var(--ds-primary-soft)] disabled:bg-surface-muted',
  },
})
export class UiSelect {}

/** Liste déroulante avec libellé toujours en haut (une valeur « Tous » vaut déjà une sélection). */
@Component({
  selector: 'ui-select-field',
  imports: [NgIcon],
  template: `
    <div class="relative">
      <ng-content />
      <label [attr.for]="inputId()" class="pointer-events-none absolute left-3 top-2.5 text-xs text-muted">{{ label() }}</label>
      <ng-icon name="lucideChevronDown" size="16" aria-hidden="true"
        class="pointer-events-none absolute right-3 top-1/2 -translate-y-1/2 text-muted" />
    </div>
  `,
  host: { class: 'block min-w-44' },
})
export class UiSelectField {
  readonly label = input.required<string>();
  readonly inputId = input.required<string>();
}

/** Case à cocher native, colorée par le thème. */
@Directive({
  selector: 'input[type=checkbox][uiCheckbox]',
  host: { class: 'h-4 w-4 cursor-pointer rounded-sm accent-[var(--ds-primary)]' },
})
export class UiCheckbox {}
