import { CurrencyPipe } from '@angular/common';
import { Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed, toObservable } from '@angular/core/rxjs-interop';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { PageChange, ToastService, UiBadge, UiButton, UiDialog, UiEmptyState, UiField, UiInput, UiPageHeader, UiPagination, UiSelect, UiSelectField, UiTable, UiTableSkeleton } from '../../shared/ui';
import { catchError, debounceTime, EMPTY, filter, switchMap } from 'rxjs';
import { AuthService } from '../../core/auth.service';
import { PharmacyPayload, PharmacyRow } from '../../core/models';
import { PharmaciesService, PharmacyQuery } from './pharmacies.service';
import { PharmacyFormDialogComponent } from './pharmacy-form-dialog';
import { PharmacyMembersDialogComponent } from './pharmacy-members-dialog';

@Component({
  selector: 'app-pharmacies-list',
  imports: [
    ReactiveFormsModule, CurrencyPipe, TranslocoPipe, UiPageHeader, UiField, UiInput, UiSelectField, UiSelect, UiTable, UiBadge, UiButton, UiPagination, UiEmptyState, UiTableSkeleton,
  ],
  templateUrl: './pharmacies-list.html',
})
export class PharmaciesListComponent {
  private readonly api = inject(PharmaciesService);
  private readonly auth = inject(AuthService);
  private readonly dialog = inject(UiDialog);
  private readonly toast = inject(ToastService);
  private readonly i18n = inject(TranslocoService);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly q = new FormControl('', { nonNullable: true });
  protected readonly onDuty = new FormControl<boolean | ''>('', { nonNullable: true });

  protected readonly query = signal<PharmacyQuery>({ page: 0, size: 20 });
  protected readonly rows = signal<PharmacyRow[]>([]);
  protected readonly total = signal(0);
  protected readonly loading = signal(false);
  private readonly reload = signal(0);

  protected readonly canEdit = () => ['SUPER', 'SUPPORT'].includes(this.auth.user()?.adminLevel ?? '');

  constructor() {
    toObservable(this.query)
      .pipe(
        switchMap((q) => this.load(q)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe((p) => {
        this.rows.set(p.content);
        this.total.set(p.totalElements);
        this.loading.set(false);
      });
    this.q.valueChanges
      .pipe(debounceTime(300), takeUntilDestroyed(this.destroyRef))
      .subscribe((q) => this.query.update((s) => ({ ...s, q, page: 0 })));
    this.onDuty.valueChanges
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((onDuty) => this.query.update((s) => ({ ...s, onDuty, page: 0 })));
  }

  protected onPage(e: PageChange): void {
    this.query.update((s) => ({ ...s, page: e.pageIndex, size: e.pageSize }));
  }

  protected edit(p: PharmacyRow | null): void {
    this.dialog
      .open<PharmacyPayload, PharmacyRow | null>(PharmacyFormDialogComponent, p)
      .pipe(
        filter((v): v is PharmacyPayload => !!v),
        switchMap((payload) => (p ? this.api.update(p.id, payload) : this.api.create(payload))),
      )
      .subscribe(() => {
        this.refresh();
        this.toast.success(this.i18n.translate('pharmacies.saved'));
      });
  }

  protected members(p: PharmacyRow): void {
    this.dialog.open<void, PharmacyRow>(PharmacyMembersDialogComponent, p).subscribe();
  }

  protected toggle(p: PharmacyRow): void {
    this.api.setActive(p.id, !p.active).subscribe((updated) => {
      this.rows.update((rows) => rows.map((r) => (r.id === updated.id ? updated : r)));
    });
  }

  private refresh(): void {
    this.reload.update((n) => n + 1);
    this.query.update((s) => ({ ...s }));
  }

  private load(q: PharmacyQuery) {
    this.loading.set(true);
    return this.api.list(q).pipe(
      catchError(() => {
        this.loading.set(false);
        return EMPTY;
      }),
    );
  }
}
