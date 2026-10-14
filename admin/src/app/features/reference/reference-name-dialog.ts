import { Component, inject } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { BrnDialogClose, BrnDialogRef, BrnDialogTitle, injectBrnDialogContext } from '@spartan-ng/brain/dialog';
import { TranslocoPipe } from '@jsverse/transloco';
import { UiButton, UiField, UiInput } from '../../shared/ui';

export interface ReferenceNameData {
  titleKey: string;
  /** Demande aussi un code (création de spécialité ou de type). */
  withCode?: boolean;
  name?: string;
}

export interface ReferenceNameResult {
  name: string;
  code: string;
}

const CODE = /^[A-Za-z0-9_]{2,50}$/;

/** Saisie d'un libellé (et d'un code à la création) pour un élément de référentiel. */
@Component({
  selector: 'app-reference-name-dialog',
  imports: [ReactiveFormsModule, TranslocoPipe, BrnDialogTitle, BrnDialogClose, UiField, UiInput, UiButton],
  template: `
    <h2 brnDialogTitle class="m-0 mb-4 text-lg font-semibold text-text">{{ data.titleKey | transloco }}</h2>
    <form [formGroup]="form" (ngSubmit)="submit()" class="grid gap-4">
      @if (data.withCode) {
        <ui-field [label]="'reference.code' | transloco" inputId="rf-code"
          [error]="form.controls.code.touched && form.controls.code.invalid ? ('reference.codeInvalid' | transloco) : null">
          <input uiInput id="rf-code" formControlName="code" maxlength="50" autocomplete="off" class="uppercase" />
        </ui-field>
      }
      <ui-field [label]="'reference.name' | transloco" inputId="rf-name"
        [error]="form.controls.name.touched && form.controls.name.invalid ? ('validation.required' | transloco) : null">
        <input uiInput id="rf-name" formControlName="name" maxlength="120" autocomplete="off" />
      </ui-field>
      <div class="mt-2 flex justify-end gap-2">
        <button uiBtn variant="ghost" type="button" brnDialogClose>{{ 'common.cancel' | transloco }}</button>
        <button uiBtn type="submit">{{ 'common.save' | transloco }}</button>
      </div>
    </form>
  `,
})
export class ReferenceNameDialogComponent {
  protected readonly data = injectBrnDialogContext<ReferenceNameData>();
  private readonly ref = inject<BrnDialogRef<ReferenceNameResult>>(BrnDialogRef);
  private readonly fb = inject(FormBuilder).nonNullable;

  protected readonly form = this.fb.group({
    code: ['', this.data.withCode ? [Validators.required, Validators.pattern(CODE)] : []],
    name: [this.data.name ?? '', [Validators.required, Validators.maxLength(120), Validators.pattern(/\S/)]],
  });

  protected submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const v = this.form.getRawValue();
    this.ref.close({ name: v.name.trim(), code: v.code.trim().toUpperCase() });
  }
}
