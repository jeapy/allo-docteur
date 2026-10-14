import { Component, inject } from '@angular/core';
import { FormControl, ReactiveFormsModule, Validators } from '@angular/forms';
import { BrnDialogClose, BrnDialogRef, BrnDialogTitle, injectBrnDialogContext } from '@spartan-ng/brain/dialog';
import { TranslocoPipe } from '@jsverse/transloco';
import { UiButton, UiField, UiInput } from '../../shared/ui';

export interface ReasonDialogData {
  titleKey: string;
  /** Libellé du champ ; « Motif » par défaut. */
  labelKey?: string;
}

@Component({
  selector: 'app-reason-dialog',
  imports: [
    ReactiveFormsModule, BrnDialogTitle, BrnDialogClose, UiButton, UiField, UiInput, TranslocoPipe,
  ],
  template: `
    <h2 brnDialogTitle class="m-0 mb-4 text-lg font-semibold text-text">{{ data.titleKey | transloco }}</h2>
    <ui-field [label]="(data.labelKey ?? 'professionals.reason') | transloco" inputId="reason-input"
      [error]="reason.touched && reason.invalid ? ('validation.required' | transloco) : null">
      <textarea uiInput id="reason-input" rows="4" maxlength="1000" class="resize-y" [formControl]="reason"></textarea>
    </ui-field>
    <div class="mt-6 flex justify-end gap-2">
      <button uiBtn variant="ghost" type="button" brnDialogClose>{{ 'common.cancel' | transloco }}</button>
      <button uiBtn type="button" [disabled]="reason.invalid" (click)="ref.close(reason.value.trim())">
        {{ 'common.confirm' | transloco }}
      </button>
    </div>
  `,
})
export class ReasonDialogComponent {
  protected readonly data = injectBrnDialogContext<ReasonDialogData>();
  protected readonly ref = inject<BrnDialogRef<string>>(BrnDialogRef);
  protected readonly reason = new FormControl('', { nonNullable: true, validators: [Validators.required, Validators.pattern(/\S/)] });
}
