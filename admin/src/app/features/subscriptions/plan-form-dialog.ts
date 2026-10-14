import { Component, inject } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { BrnDialogClose, BrnDialogRef, BrnDialogTitle, injectBrnDialogContext } from '@spartan-ng/brain/dialog';
import { TranslocoPipe } from '@jsverse/transloco';
import { UiButton, UiCheckbox, UiField, UiInput } from '../../shared/ui';
import { PlanPayload, PlanRow } from '../../core/models';

/** Modification d'une formule d'abonnement. Le taux de commission se saisit en pourcentage. */
@Component({
  selector: 'app-plan-form-dialog',
  imports: [
    ReactiveFormsModule, TranslocoPipe, BrnDialogTitle, BrnDialogClose, UiField, UiInput, UiButton, UiCheckbox,
  ],
  templateUrl: './plan-form-dialog.html',
})
export class PlanFormDialogComponent {
  protected readonly plan = injectBrnDialogContext<PlanRow>();
  private readonly ref = inject<BrnDialogRef<PlanPayload>>(BrnDialogRef);
  private readonly fb = inject(FormBuilder).nonNullable;

  protected readonly form = this.fb.group({
    name: [this.plan.name, [Validators.required, Validators.maxLength(100), Validators.pattern(/\S/)]],
    description: [this.plan.description ?? '', [Validators.maxLength(500)]],
    price: [this.plan.price, [Validators.required, Validators.min(0)]],
    durationDays: [this.plan.durationDays, [Validators.required, Validators.min(1), Validators.max(3650)]],
    commissionPercent: [
      this.plan.commissionRate == null ? (null as number | null) : Math.round(this.plan.commissionRate * 10000) / 100,
      [Validators.min(0), Validators.max(100)],
    ],
    active: [this.plan.active],
  });

  protected get isProfessional(): boolean {
    return this.plan.audience === 'PROFESSIONAL';
  }

  protected submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const v = this.form.getRawValue();
    this.ref.close({
      name: v.name.trim(),
      description: v.description.trim() || null,
      price: v.price,
      durationDays: v.durationDays,
      commissionRate: this.isProfessional && v.commissionPercent !== null ? v.commissionPercent / 100 : null,
      active: v.active,
    });
  }
}
