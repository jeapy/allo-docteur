import { Component, DestroyRef, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { Observable, filter, switchMap, tap } from 'rxjs';
import { CityAdmin, ProfessionalTypeAdmin, SpecialtyAdmin } from '../../core/models';
import { ToastService, UiBadge, UiButton, UiDialog, UiEmptyState, UiPageHeader, UiTab, UiTable, UiTabs } from '../../shared/ui';
import { ReferenceNameData, ReferenceNameDialogComponent, ReferenceNameResult } from './reference-name-dialog';
import { ReferenceAdminService } from './reference.service';

/** Référentiels de la plateforme (niveau SUPER). Rien n'est supprimé : on désactive. */
@Component({
  selector: 'app-reference-list',
  imports: [TranslocoPipe, UiPageHeader, UiTabs, UiTable, UiBadge, UiButton, UiEmptyState],
  templateUrl: './reference-list.html',
})
export class ReferenceListComponent {
  private readonly api = inject(ReferenceAdminService);
  private readonly dialog = inject(UiDialog);
  private readonly toast = inject(ToastService);
  private readonly i18n = inject(TranslocoService);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly tab = signal('cities');
  protected readonly tabs = computed<UiTab[]>(() => [
    { id: 'cities', labelKey: 'reference.tabCities' },
    { id: 'specialties', labelKey: 'reference.tabSpecialties' },
    { id: 'types', labelKey: 'reference.tabTypes' },
  ]);

  protected readonly cities = signal<CityAdmin[]>([]);
  protected readonly specialties = signal<SpecialtyAdmin[]>([]);
  protected readonly types = signal<ProfessionalTypeAdmin[]>([]);
  protected readonly expanded = signal<number | null>(null);

  constructor() {
    this.api.cities().pipe(takeUntilDestroyed(this.destroyRef)).subscribe((c) => this.cities.set(c));
    this.api.specialties().pipe(takeUntilDestroyed(this.destroyRef)).subscribe((s) => this.specialties.set(s));
    this.api.types().pipe(takeUntilDestroyed(this.destroyRef)).subscribe((t) => this.types.set(t));
  }

  protected toggleCity(c: CityAdmin): void {
    this.expanded.update((id) => (id === c.id ? null : c.id));
  }

  // ---- villes et quartiers
  protected addCity(): void {
    this.ask({ titleKey: 'reference.addCity' }, (r) => this.api.createCity(r.name)).subscribe((c) => this.cities.update((l) => this.sorted([...l, c])));
  }
  protected renameCity(c: CityAdmin): void {
    this.ask({ titleKey: 'reference.rename', name: c.name }, (r) => this.api.renameCity(c.id, r.name)).subscribe((u) => this.replaceCity(u));
  }
  protected toggleCityActive(c: CityAdmin): void {
    this.api.setCityActive(c.id, !c.active).subscribe((u) => this.replaceCity(u));
  }
  protected addDistrict(c: CityAdmin): void {
    this.ask({ titleKey: 'reference.addDistrict' }, (r) => this.api.createDistrict(c.id, r.name)).subscribe((u) => this.replaceCity(u));
  }
  protected renameDistrict(d: { id: number; name: string }): void {
    this.ask({ titleKey: 'reference.rename', name: d.name }, (r) => this.api.renameDistrict(d.id, r.name)).subscribe((u) => this.replaceCity(u));
  }
  protected toggleDistrictActive(d: { id: number; active: boolean }): void {
    this.api.setDistrictActive(d.id, !d.active).subscribe((u) => this.replaceCity(u));
  }

  // ---- spécialités
  protected addSpecialty(): void {
    this.ask({ titleKey: 'reference.addSpecialty', withCode: true }, (r) => this.api.createSpecialty(r.code, r.name)).subscribe((s) =>
      this.specialties.update((l) => [...l, s].sort((a, b) => a.name.localeCompare(b.name, 'fr'))),
    );
  }
  protected renameSpecialty(s: SpecialtyAdmin): void {
    this.ask({ titleKey: 'reference.rename', name: s.name }, (r) => this.api.renameSpecialty(s.id, r.name)).subscribe((u) =>
      this.specialties.update((l) => l.map((x) => (x.id === u.id ? u : x))),
    );
  }
  protected toggleSpecialtyActive(s: SpecialtyAdmin): void {
    this.api.setSpecialtyActive(s.id, !s.active).subscribe((u) => this.specialties.update((l) => l.map((x) => (x.id === u.id ? u : x))));
  }

  // ---- types de professionnels
  protected addType(): void {
    this.ask({ titleKey: 'reference.addType', withCode: true }, (r) => this.api.createType(r.code, r.name)).subscribe((t) =>
      this.types.update((l) => [...l, t].sort((a, b) => a.label.localeCompare(b.label, 'fr'))),
    );
  }
  protected renameType(t: ProfessionalTypeAdmin): void {
    this.ask({ titleKey: 'reference.rename', name: t.label }, (r) => this.api.renameType(t.id, r.name)).subscribe((u) =>
      this.types.update((l) => l.map((x) => (x.id === u.id ? u : x))),
    );
  }

  private replaceCity(u: CityAdmin): void {
    this.cities.update((l) => l.map((x) => (x.id === u.id ? u : x)));
  }

  private sorted(list: CityAdmin[]): CityAdmin[] {
    return list.sort((a, b) => a.name.localeCompare(b.name, 'fr'));
  }

  /** Ouvre la saisie puis exécute l'appel ; confirme par une notification. */
  private ask<T>(data: ReferenceNameData, call: (r: ReferenceNameResult) => Observable<T>): Observable<T> {
    return this.dialog.open<ReferenceNameResult, ReferenceNameData>(ReferenceNameDialogComponent, data).pipe(
      filter((r): r is ReferenceNameResult => !!r),
      switchMap((r) => call(r)),
      tap(() => this.toast.success(this.i18n.translate('reference.saved'))),
    );
  }
}
