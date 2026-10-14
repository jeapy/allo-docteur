import { Component, Directive, ElementRef, inject, input } from '@angular/core';
import { NgControl } from '@angular/forms';

/**
 * Champ de saisie du design system. À placer dans `<ui-field>` :
 * `<input uiInput id="email" formControlName="email" />`.
 * Le `placeholder=" "` permet au libellé flottant de savoir si le champ est rempli (CSS `:placeholder-shown`).
 */
@Directive({
  selector: 'input[uiInput], textarea[uiInput]',
  host: {
    class:
      'peer block w-full rounded-sm border bg-surface px-3 pt-5 pb-1.5 text-base text-text shadow-none ' +
      'transition-colors duration-150 ease-ds placeholder-transparent focus:outline-none ' +
      'focus:border-primary focus:shadow-[0_0_0_3px_var(--ds-primary-soft)] disabled:bg-surface-muted',
    placeholder: ' ',
    '[class.border-border-strong]': '!invalid',
    '[class.border-danger]': 'invalid',
    '[attr.aria-invalid]': 'invalid',
  },
})
export class UiInput {
  private readonly control = inject(NgControl, { optional: true, self: true });
  readonly el = inject<ElementRef<HTMLElement>>(ElementRef);

  /** Invalide *et* touché : on ne crie pas sur un champ que personne n'a encore rempli. */
  protected get invalid(): boolean {
    const c = this.control?.control;
    return !!c && c.invalid && (c.touched || c.dirty);
  }
}

/** Libellé flottant + message d'erreur relié au champ (`aria-describedby` à poser sur l'input si besoin). */
@Component({
  selector: 'ui-field',
  template: `
    <div class="relative">
      <ng-content />
      <label
        [attr.for]="inputId()"
        class="pointer-events-none absolute left-3 top-1/2 origin-left -translate-y-1/2 bg-transparent text-base text-muted
               transition-all duration-150 ease-ds
               peer-focus:top-2.5 peer-focus:text-xs peer-focus:text-primary
               peer-[:not(:placeholder-shown)]:top-2.5 peer-[:not(:placeholder-shown)]:text-xs"
        >{{ label() }}</label
      >
    </div>
    @if (error()) {
      <p [id]="inputId() + '-error'" role="alert" class="m-0 mt-1 text-xs font-medium text-danger-text">{{ error() }}</p>
    }
  `,
  host: { class: 'block' },
})
export class UiField {
  readonly label = input.required<string>();
  /** Identifiant de l'input projeté (lie le libellé à son champ). */
  readonly inputId = input.required<string>();
  readonly error = input<string | null>(null);
}
