import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { BrnDialogClose, BrnDialogRef, BrnDialogTitle, injectBrnDialogContext } from '@spartan-ng/brain/dialog';
import { TranslocoPipe } from '@jsverse/transloco';
import { UiButton, UiCheckbox, UiField, UiInput, UiSelect, UiSelectField } from '../../shared/ui';
import { CityRef, PharmacyPayload, PharmacyRow } from '../../core/models';
import { PharmaciesService } from './pharmacies.service';

const PHONE = /^\+?[0-9 ()-]{8,20}$/;

/** Création / modification d'une pharmacie partenaire. */
@Component({
  selector: 'app-pharmacy-form-dialog',
  imports: [
    ReactiveFormsModule, TranslocoPipe, BrnDialogTitle, BrnDialogClose, UiField, UiInput, UiSelectField, UiSelect, UiButton, UiCheckbox,
  ],
  templateUrl: './pharmacy-form-dialog.html',
})
export class PharmacyFormDialogComponent {
  protected readonly existing = injectBrnDialogContext<PharmacyRow | null>();
  private readonly ref = inject<BrnDialogRef<PharmacyPayload>>(BrnDialogRef);
  private readonly fb = inject(FormBuilder).nonNullable;

  protected readonly cities = signal<CityRef[]>([]);

  protected readonly form = this.fb.group({
    name: [this.existing?.name ?? '', [Validators.required, Validators.maxLength(200), Validators.pattern(/\S/)]],
    cityId: [this.existing?.cityId ?? 0, [Validators.required, Validators.min(1)]],
    address: [this.existing?.address ?? '', [Validators.required, Validators.maxLength(255), Validators.pattern(/\S/)]],
    phone: [this.existing?.phone ?? '', [Validators.required, Validators.pattern(PHONE)]],
    latitude: [this.existing?.latitude ?? (null as number | null), [Validators.min(-90), Validators.max(90)]],
    longitude: [this.existing?.longitude ?? (null as number | null), [Validators.min(-180), Validators.max(180)]],
    openingHours: [this.existing?.openingHours ?? '', [Validators.maxLength(255)]],
    onDuty: [this.existing?.onDuty ?? false],
    offersDelivery: [this.existing?.offersDelivery ?? false],
    deliveryFee: [this.existing?.deliveryFee ?? (0 as number | null), [Validators.min(0)]],
  });

  constructor() {
    inject(PharmaciesService)
      .cities()
      .subscribe((c) => this.cities.set(c));
  }

  /** Latitude et longitude vont ensemble. */
  protected get coordinatesIncomplete(): boolean {
    const { latitude, longitude } = this.form.getRawValue();
    return (latitude === null) !== (longitude === null);
  }

  protected submit(): void {
    if (this.form.invalid || this.coordinatesIncomplete) {
      this.form.markAllAsTouched();
      return;
    }
    const v = this.form.getRawValue();
    this.ref.close({
      name: v.name.trim(),
      cityId: v.cityId,
      address: v.address.trim(),
      phone: v.phone.trim(),
      latitude: v.latitude,
      longitude: v.longitude,
      openingHours: v.openingHours.trim() || null,
      onDuty: v.onDuty,
      offersDelivery: v.offersDelivery,
      deliveryFee: v.offersDelivery ? (v.deliveryFee ?? 0) : null,
    });
  }
}
