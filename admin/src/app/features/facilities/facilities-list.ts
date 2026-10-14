import { Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed, toObservable } from '@angular/core/rxjs-interop';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { catchError, EMPTY, filter, switchMap } from 'rxjs';
import { AuthService } from '../../core/auth.service';
import { CityRef, FacilityPayload, FacilityRow } from '../../core/models';
import { PageChange, ToastService, UiBadge, UiButton, UiDialog, UiEmptyState, UiPageHeader, UiPagination, UiSelect, UiSelectField, UiTable, UiTableSkeleton } from '../../shared/ui';
import { FacilityFormDialogComponent } from './facility-form-dialog';
import { FacilitiesService, FacilityQuery } from './facilities.service';

/** Structures de santé vers lesquelles le SOS oriente les patients. */
@Component({
  selector: 'app-facilities-list',
  imports: [ReactiveFormsModule, TranslocoPipe, UiPageHeader, UiSelectField, UiSelect, UiTable, UiBadge, UiButton, UiPagination, UiEmptyState, UiTableSkeleton],
  templateUrl: './facilities-list.html',
})
export class FacilitiesListComponent {
  private readonly api = inject(FacilitiesService);
  private readonly auth = inject(AuthService);
  private readonly dialog = inject(UiDialog);
  private readonly toast = inject(ToastService);
  private readonly i18n = inject(TranslocoService);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly cities = signal<CityRef[]>([]);
  protected readonly cityId = new FormControl<number | ''>('', { nonNullable: true });

  protected readonly query = signal<FacilityQuery>({ page: 0, size: 20 });
  protected readonly rows = signal<FacilityRow[]>([]);
  protected readonly total = signal(0);
  protected readonly loading = signal(false);

  protected readonly canEdit = () => ['SUPER', 'SUPPORT'].includes(this.auth.user()?.adminLevel ?? '');

  constructor() {
    this.api.cities().pipe(takeUntilDestroyed(this.destroyRef)).subscribe((c) => this.cities.set(c));

    toObservable(this.query)
      .pipe(
        switchMap((q) => {
          this.loading.set(true);
          return this.api.list(q).pipe(
            catchError(() => {
              this.loading.set(false);
              return EMPTY;
            }),
          );
        }),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe((p) => {
        this.rows.set(p.content);
        this.total.set(p.totalElements);
        this.loading.set(false);
      });

    this.cityId.valueChanges.pipe(takeUntilDestroyed(this.destroyRef)).subscribe((cityId) => this.query.update((q) => ({ ...q, cityId, page: 0 })));
  }

  protected onPage(e: PageChange): void {
    this.query.update((q) => ({ ...q, page: e.pageIndex, size: e.pageSize }));
  }

  protected edit(f: FacilityRow | null): void {
    this.dialog
      .open<FacilityPayload, FacilityRow | null>(FacilityFormDialogComponent, f)
      .pipe(
        filter((v): v is FacilityPayload => !!v),
        switchMap((payload) => (f ? this.api.update(f.id, payload) : this.api.create(payload))),
      )
      .subscribe(() => {
        this.query.update((q) => ({ ...q }));
        this.toast.success(this.i18n.translate('facilities.saved'));
      });
  }

  protected toggle(f: FacilityRow): void {
    this.api.setActive(f.id, !f.active).subscribe((updated) => this.rows.update((rows) => rows.map((r) => (r.id === updated.id ? updated : r))));
  }
}
