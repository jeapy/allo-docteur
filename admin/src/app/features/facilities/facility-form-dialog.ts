import { Component, computed, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { BrnDialogClose, BrnDialogRef, BrnDialogTitle } from '@spartan-ng/brain/dialog';
import { TranslocoPipe } from '@jsverse/transloco';
import { CityRef, FacilityPayload, FacilityRow, FacilityType } from '../../core/models';
import { UiButton, UiCheckbox, UiField, UiInput, UiSelect, UiSelectField, injectDialogEntity } from '../../shared/ui';
import { FacilitiesService } from './facilities.service';

const PHONE = /^\+?[0-9 ()-]{8,20}$/;

/** Création / modification d'une structure de santé (orientation SOS). */
@Component({
  selector: 'app-facility-form-dialog',
  imports: [ReactiveFormsModule, TranslocoPipe, BrnDialogTitle, BrnDialogClose, UiField, UiInput, UiSelectField, UiSelect, UiButton, UiCheckbox],
  templateUrl: './facility-form-dialog.html',
})
export class FacilityFormDialogComponent {
  protected readonly existing = injectDialogEntity<FacilityRow>();
  private readonly ref = inject<BrnDialogRef<FacilityPayload>>(BrnDialogRef);
  private readonly fb = inject(FormBuilder).nonNullable;

  protected readonly types: FacilityType[] = ['HOSPITAL', 'CLINIC', 'HEALTH_CENTER'];
  protected readonly cities = signal<CityRef[]>([]);

  protected readonly form = this.fb.group({
    name: [this.existing?.name ?? '', [Validators.required, Validators.maxLength(200), Validators.pattern(/\S/)]],
    type: [this.existing?.type ?? ('HOSPITAL' as FacilityType), [Validators.required]],
    cityId: [this.existing?.cityId ?? 0, [Validators.required, Validators.min(1)]],
    districtId: [this.existing?.districtId ?? (null as number | null)],
    address: [this.existing?.address ?? '', [Validators.maxLength(255)]],
    phone: [this.existing?.phone ?? '', [Validators.pattern(PHONE)]],
    latitude: [this.existing?.latitude ?? (null as number | null), [Validators.required, Validators.min(-90), Validators.max(90)]],
    longitude: [this.existing?.longitude ?? (null as number | null), [Validators.required, Validators.min(-180), Validators.max(180)]],
    emergencyService: [this.existing?.emergencyService ?? true],
  });

  private readonly cityId = toSignal(this.form.controls.cityId.valueChanges, { initialValue: this.form.controls.cityId.value });
  protected readonly districts = computed(() => this.cities().find((c) => c.id === this.cityId())?.districts ?? []);

  constructor() {
    inject(FacilitiesService)
      .cities()
      .subscribe((c) => this.cities.set(c));
    // un quartier n'a de sens que dans sa ville
    this.form.controls.cityId.valueChanges.subscribe(() => this.form.controls.districtId.setValue(null));
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
      districtId: v.districtId,
      address: v.address.trim() || null,
      phone: v.phone.trim() || null,
      latitude: v.latitude as number,
      longitude: v.longitude as number,
      emergencyService: v.emergencyService,
    });
  }
}
