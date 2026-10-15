import { DecimalPipe } from '@angular/common';
import { Component, DestroyRef, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import { AbstractControl, FormBuilder, ReactiveFormsModule, ValidationErrors, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { NgIcon } from '@ng-icons/core';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { EMPTY, catchError, combineLatest, debounceTime, distinctUntilChanged, filter, map, of, startWith, switchMap, tap } from 'rxjs';
import { CityRef, TypeRef } from '../../core/models';
import { UiButton, UiCheckbox, UiField, UiInput, UiSelect, UiSelectField } from '../../shared/ui';
import {
  ACCEPTED_TYPES, ApplicationDocument, ApplicationReceipt, ApplyService, DocumentKind, LicenseCheckResult,
  MAX_FILES, SpecialtyRef, fileProblem,
} from './apply.service';

const PHONE = /^\+[1-9]\d{7,14}$/;

/** Au moins une prestation, et le tarif correspondant. */
function offerValidator(group: AbstractControl): ValidationErrors | null {
  const v = group.value as { offersTeleconsultation: boolean; offersHomeVisit: boolean; consultationFee: number | null; homeVisitFee: number | null };
  if (!v.offersTeleconsultation && !v.offersHomeVisit) return { noOffer: true };
  if (v.offersTeleconsultation && v.consultationFee == null) return { teleFee: true };
  if (v.offersHomeVisit && v.homeVisitFee == null) return { homeFee: true };
  return null;
}

/** Candidature publique d'un professionnel : aucun compte requis, validée ensuite par l'administration. */
@Component({
  selector: 'app-apply',
  imports: [
    ReactiveFormsModule, RouterLink, NgIcon, DecimalPipe, TranslocoPipe,
    UiButton, UiCheckbox, UiField, UiInput, UiSelect, UiSelectField,
  ],
  styleUrls: ['../login/login.scss', './apply.scss'],
  templateUrl: './apply.html',
})
export class ApplyComponent {
  private readonly api = inject(ApplyService);
  private readonly fb = inject(FormBuilder).nonNullable;
  private readonly destroyRef = inject(DestroyRef);
  private readonly i18n = inject(TranslocoService);

  protected readonly accept = ACCEPTED_TYPES.join(',');
  protected readonly maxFiles = MAX_FILES;
  protected readonly kinds: DocumentKind[] = ['LICENSE', 'DIPLOMA', 'ID_CARD', 'OTHER'];

  protected readonly types = toSignal(this.api.types().pipe(catchError(() => of([] as TypeRef[]))), { initialValue: [] as TypeRef[] });
  protected readonly specialties = toSignal(this.api.specialties().pipe(catchError(() => of([] as SpecialtyRef[]))), { initialValue: [] as SpecialtyRef[] });
  protected readonly cities = toSignal(this.api.cities().pipe(catchError(() => of([] as CityRef[]))), { initialValue: [] as CityRef[] });

  protected readonly form = this.fb.group({
    account: this.fb.group({
      firstName: ['', [Validators.required, Validators.maxLength(100)]],
      lastName: ['', [Validators.required, Validators.maxLength(100)]],
      email: ['', [Validators.required, Validators.email, Validators.maxLength(255)]],
      phone: ['', [Validators.required, Validators.pattern(PHONE)]],
      password: ['', [Validators.required, Validators.minLength(10), Validators.maxLength(128)]],
    }),
    profile: this.fb.group(
      {
        professionalTypeId: [null as number | null, [Validators.required]],
        qualification: ['', [Validators.required, Validators.maxLength(150)]],
        licenseNumber: ['', [Validators.required, Validators.maxLength(100)]],
        yearsExperience: [null as number | null, [Validators.min(0), Validators.max(70)]],
        cityId: [null as number | null, [Validators.required]],
        districtId: [null as number | null],
        offersTeleconsultation: [true],
        offersHomeVisit: [false],
        consultationFee: [null as number | null, [Validators.min(0)]],
        homeVisitFee: [null as number | null, [Validators.min(0)]],
        specialtyIds: [[] as number[], [Validators.required, Validators.minLength(1)]],
        bio: ['', [Validators.maxLength(4000)]],
      },
      { validators: offerValidator },
    ),
    acceptTerms: [false, [Validators.requiredTrue]],
  });

  protected readonly documents = signal<ApplicationDocument[]>([]);
  protected readonly newKind = signal<DocumentKind>('LICENSE');
  protected readonly documentProblem = computed(() => fileProblem(this.documents()));
  protected readonly filesTouched = signal(false);

  protected readonly license = signal<LicenseCheckResult | null>(null);
  protected readonly licenseChecking = signal(false);

  protected readonly submitting = signal(false);
  protected readonly receipt = signal<ApplicationReceipt | null>(null);

  private readonly cityId = toSignal(
    this.form.controls.profile.controls.cityId.valueChanges.pipe(startWith(this.form.controls.profile.controls.cityId.value)),
    { initialValue: null },
  );
  protected readonly districts = computed(() => this.cities().find((c) => c.id === Number(this.cityId()))?.districts ?? []);
  private readonly offers = toSignal(
    this.form.controls.profile.valueChanges.pipe(map(() => this.form.controls.profile.getRawValue())),
    { initialValue: this.form.controls.profile.getRawValue() },
  );
  protected readonly offersTele = computed(() => this.offers().offersTeleconsultation);
  protected readonly offersHome = computed(() => this.offers().offersHomeVisit);

  constructor() {
    const p = this.form.controls.profile.controls;
    // le quartier choisi n'a plus de sens si la ville change
    p.cityId.valueChanges.pipe(takeUntilDestroyed(this.destroyRef)).subscribe(() => p.districtId.setValue(null));

    // contrôle en direct du numéro d'ordre (saisie temporisée, sans enregistrement)
    combineLatest([p.professionalTypeId.valueChanges.pipe(startWith(p.professionalTypeId.value)), p.licenseNumber.valueChanges.pipe(startWith(''))])
      .pipe(
        tap(() => this.license.set(null)),
        debounceTime(450),
        map(([type, number]) => [type, number.trim()] as const),
        distinctUntilChanged((a, b) => a[0] === b[0] && a[1] === b[1]),
        filter((x): x is readonly [number, string] => x[0] != null && x[1].length >= 4),
        tap(() => this.licenseChecking.set(true)),
        switchMap(([type, number]) =>
          this.api.checkLicense(Number(type), number).pipe(
            catchError(() => {
              this.licenseChecking.set(false);
              return EMPTY;
            }),
          ),
        ),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe((r) => {
        this.licenseChecking.set(false);
        this.license.set(r);
      });
  }

  /** Message d'erreur d'un champ une fois touché (null si valide ou intact). */
  protected msg(path: string): string | null {
    const c = this.form.get(path);
    if (!c || !c.invalid || !(c.touched || c.dirty)) return null;
    const e = c.errors ?? {};
    if (e['required'] || e['requiredTrue'] || e['minlength'] && Array.isArray(c.value)) return this.i18n.translate('validation.required');
    if (e['email']) return this.i18n.translate('validation.email');
    if (e['pattern']) return this.i18n.translate('validation.phone');
    if (e['minlength']) return this.i18n.translate('password.tooShort', { min: 10 });
    if (e['min'] || e['max']) return this.i18n.translate('validation.range');
    return this.i18n.translate('validation.required');
  }

  /** Erreur d'offre (prestation ou tarif manquant), affichée sous les cases à cocher. */
  protected offerError(): string | null {
    const g = this.form.controls.profile;
    if (!(g.touched || g.dirty) || !g.errors) return null;
    const key = g.errors['noOffer'] ? 'apply.noOffer' : g.errors['teleFee'] ? 'apply.teleFee' : 'apply.homeFee';
    return this.i18n.translate(key);
  }

  protected toggleSpecialty(id: number, checked: boolean): void {
    const ctrl = this.form.controls.profile.controls.specialtyIds;
    const set = new Set(ctrl.value);
    if (checked) set.add(id); else set.delete(id);
    ctrl.setValue([...set]);
    ctrl.markAsTouched();
  }

  protected isChosen(id: number): boolean {
    return this.form.controls.profile.controls.specialtyIds.value.includes(id);
  }

  protected fileProblemText(): string | null {
    const p = this.documentProblem();
    return p && this.filesTouched() ? this.i18n.translate('apply.file.' + p) : null;
  }

  protected setKind(value: string): void {
    this.newKind.set(value as DocumentKind);
  }

  protected addFile(input: HTMLInputElement): void {
    const files = Array.from(input.files ?? []);
    input.value = '';
    if (!files.length) return;
    this.filesTouched.set(true);
    // les fichiers en trop restent comptés pour que l'erreur « trop de fichiers » s'affiche
    this.documents.update((d) => [...d, ...files.map((file) => ({ type: this.newKind(), file }))]);
  }

  protected removeFile(index: number): void {
    this.documents.update((d) => d.filter((_, i) => i !== index));
    this.filesTouched.set(true);
  }

  protected setDocKind(index: number, value: string): void {
    this.documents.update((d) => d.map((x, i) => (i === index ? { ...x, type: value as DocumentKind } : x)));
  }

  protected submit(): void {
    this.form.markAllAsTouched();
    this.filesTouched.set(true);
    if (this.form.invalid || this.documentProblem() || this.submitting() || this.license()?.status === 'ALREADY_REGISTERED' || this.license()?.status === 'INVALID_FORMAT') return;
    const v = this.form.getRawValue();
    const pr = v.profile;
    this.submitting.set(true);
    this.api
      .submit(
        {
          account: { ...v.account, email: v.account.email.trim(), phone: v.account.phone.trim() },
          profile: {
            professionalTypeId: Number(pr.professionalTypeId),
            qualification: pr.qualification.trim(),
            licenseNumber: pr.licenseNumber.trim(),
            yearsExperience: pr.yearsExperience,
            bio: pr.bio.trim() || null,
            cityId: Number(pr.cityId),
            districtId: pr.districtId ? Number(pr.districtId) : null,
            consultationFee: pr.offersTeleconsultation ? pr.consultationFee : null,
            homeVisitFee: pr.offersHomeVisit ? pr.homeVisitFee : null,
            offersTeleconsultation: pr.offersTeleconsultation,
            offersHomeVisit: pr.offersHomeVisit,
            specialtyIds: pr.specialtyIds,
            coverageZones: [],
          },
          acceptTerms: v.acceptTerms,
        },
        this.documents(),
      )
      .subscribe({
        next: (r) => {
          this.submitting.set(false);
          this.receipt.set(r);
        },
        error: () => this.submitting.set(false), // l'intercepteur affiche le détail (doublon, format, taille…)
      });
  }
}

