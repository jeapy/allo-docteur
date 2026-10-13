import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { TranslocoPipe } from '@jsverse/transloco';
import { CityRef, PartnerPayload, PartnerRow, PartnerType } from '../../core/models';
import { PartnersService } from './partners.service';

export const PARTNER_TYPES: PartnerType[] = [
  'CLINIC', 'HEALTH_CENTER', 'HOSPITAL', 'PHARMACY', 'LAB', 'AMBULANCE', 'INSURANCE', 'MUTUAL',
];

const PHONE = /^\+?[0-9 ()-]{8,20}$/;

/** Création / modification d'un partenaire. */
@Component({
  selector: 'app-partner-form-dialog',
  imports: [
    ReactiveFormsModule, MatDialogModule, MatFormFieldModule, MatInputModule, MatSelectModule, MatButtonModule,
    TranslocoPipe,
  ],
  templateUrl: './partner-form-dialog.html',
  styleUrl: './partner-form-dialog.scss',
})
export class PartnerFormDialogComponent {
  protected readonly existing = inject<PartnerRow | null>(MAT_DIALOG_DATA);
  private readonly ref = inject(MatDialogRef<PartnerFormDialogComponent, PartnerPayload>);
  private readonly fb = inject(FormBuilder).nonNullable;

  protected readonly types = PARTNER_TYPES;
  protected readonly cities = signal<CityRef[]>([]);

  protected readonly form = this.fb.group({
    name: [this.existing?.name ?? '', [Validators.required, Validators.maxLength(150), Validators.pattern(/\S/)]],
    type: [this.existing?.type ?? ('CLINIC' as PartnerType), [Validators.required]],
    cityId: [this.existing?.cityId ?? (null as number | null)],
    address: [this.existing?.address ?? '', [Validators.maxLength(255)]],
    contactEmail: [this.existing?.contactEmail ?? '', [Validators.email, Validators.maxLength(255)]],
    contactPhone: [this.existing?.contactPhone ?? '', [Validators.pattern(PHONE)]],
    contractStartDate: [this.existing?.contractStartDate ?? ''],
  });

  constructor() {
    inject(PartnersService)
      .cities()
      .subscribe((c) => this.cities.set(c));
  }

  protected submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const v = this.form.getRawValue();
    this.ref.close({
      name: v.name.trim(),
      type: v.type,
      cityId: v.cityId,
      address: v.address.trim() || null,
      contactEmail: v.contactEmail.trim() || null,
      contactPhone: v.contactPhone.trim() || null,
      contractStartDate: v.contractStartDate || null,
    });
  }
}
