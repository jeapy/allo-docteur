import { Component, inject } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { TranslocoPipe } from '@jsverse/transloco';
import { PlanPayload, PlanRow } from '../../core/models';

/** Modification d'une formule d'abonnement. Le taux de commission se saisit en pourcentage. */
@Component({
  selector: 'app-plan-form-dialog',
  imports: [
    ReactiveFormsModule, MatDialogModule, MatFormFieldModule, MatInputModule, MatCheckboxModule, MatButtonModule,
    TranslocoPipe,
  ],
  templateUrl: './plan-form-dialog.html',
  styleUrl: './plan-form-dialog.scss',
})
export class PlanFormDialogComponent {
  protected readonly plan = inject<PlanRow>(MAT_DIALOG_DATA);
  private readonly ref = inject(MatDialogRef<PlanFormDialogComponent, PlanPayload>);
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
