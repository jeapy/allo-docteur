import { CurrencyPipe, DatePipe } from '@angular/common';
import { Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed, toObservable } from '@angular/core/rxjs-interop';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { PageChange, SortChange, ToastService, UiBadge, UiButton, UiDialog, UiEmptyState, UiField, UiInput, UiPageHeader, UiPagination, UiSelect, UiSelectField, UiSortHeader, UiTable, UiTableSkeleton, statusTone } from '../../shared/ui';
import { catchError, EMPTY, filter, switchMap } from 'rxjs';
import { AuthService } from '../../core/auth.service';
import { AppointmentRow, AppointmentStatus, AppointmentType } from '../../core/models';
import { ReasonDialogComponent } from '../professionals/reason-dialog';
import { AppointmentQuery, AppointmentsService } from './appointments.service';

@Component({
  selector: 'app-appointments-list',
  imports: [
    ReactiveFormsModule, DatePipe, CurrencyPipe, TranslocoPipe, UiPageHeader, UiField, UiInput, UiSelectField, UiSelect, UiTable, UiSortHeader, UiBadge, UiButton, UiPagination, UiEmptyState, UiTableSkeleton,
  ],
  templateUrl: './appointments-list.html',
})
export class AppointmentsListComponent {
  private readonly api = inject(AppointmentsService);
  private readonly auth = inject(AuthService);
  private readonly dialog = inject(UiDialog);
  private readonly toast = inject(ToastService);
  private readonly i18n = inject(TranslocoService);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly tone = statusTone;
  protected readonly statuses: AppointmentStatus[] = ['REQUESTED', 'CONFIRMED', 'IN_PROGRESS', 'COMPLETED', 'CANCELLED', 'NO_SHOW'];
  protected readonly types: AppointmentType[] = ['TELECONSULTATION_VIDEO', 'TELECONSULTATION_AUDIO', 'CHAT', 'HOME_VISIT'];

  protected readonly status = new FormControl<AppointmentStatus | ''>('', { nonNullable: true });
  protected readonly type = new FormControl<AppointmentType | ''>('', { nonNullable: true });
  protected readonly from = new FormControl('', { nonNullable: true });
  protected readonly to = new FormControl('', { nonNullable: true });

  protected readonly query = signal<AppointmentQuery>({ page: 0, size: 20, sort: 'startAt,desc' });
  protected readonly rows = signal<AppointmentRow[]>([]);
  protected readonly total = signal(0);
  protected readonly loading = signal(false);

  /** Seuls les niveaux SUPER et SUPPORT peuvent annuler ; FINANCE consulte en lecture seule. */
  protected readonly canCancel = () => ['SUPER', 'SUPPORT'].includes(this.auth.user()?.adminLevel ?? '');

  constructor() {
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

    this.status.valueChanges.pipe(takeUntilDestroyed(this.destroyRef)).subscribe((status) => this.patch({ status, page: 0 }));
    this.type.valueChanges.pipe(takeUntilDestroyed(this.destroyRef)).subscribe((type) => this.patch({ type, page: 0 }));
    this.from.valueChanges
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((d) => this.patch({ from: d ? new Date(`${d}T00:00:00`).toISOString() : undefined, page: 0 }));
    this.to.valueChanges.pipe(takeUntilDestroyed(this.destroyRef)).subscribe((d) => {
      // borne haute exclusive : on inclut toute la journée choisie
      const end = d ? new Date(`${d}T00:00:00`) : null;
      if (end) end.setDate(end.getDate() + 1);
      this.patch({ to: end ? end.toISOString() : undefined, page: 0 });
    });
  }

  protected onPage(e: PageChange): void {
    this.patch({ page: e.pageIndex, size: e.pageSize });
  }

  protected onSort(s: SortChange): void {
    this.patch({ sort: s.direction ? `${s.active},${s.direction}` : undefined, page: 0 });
  }

  protected cancellable(a: AppointmentRow): boolean {
    return this.canCancel() && (a.status === 'REQUESTED' || a.status === 'CONFIRMED');
  }

  protected cancel(a: AppointmentRow): void {
    this.dialog
      .open<string, { titleKey: string }>(ReasonDialogComponent, { titleKey: 'appointments.cancelTitle' })
      .pipe(
        filter((r): r is string => !!r),
        switchMap((reason) => this.api.cancel(a.id, reason)),
      )
      .subscribe((updated) => {
        this.rows.update((rows) => rows.map((r) => (r.id === updated.id ? updated : r)));
        this.toast.success(this.i18n.translate('appointments.cancelled'));
      });
  }

  private patch(change: Partial<AppointmentQuery>): void {
    this.query.update((q) => ({ ...q, ...change }));
  }
}
