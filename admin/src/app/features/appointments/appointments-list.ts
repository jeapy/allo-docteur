import { CurrencyPipe, DatePipe } from '@angular/common';
import { Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed, toObservable } from '@angular/core/rxjs-interop';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSelectModule } from '@angular/material/select';
import { MatSnackBar } from '@angular/material/snack-bar';
import { MatSortModule, Sort } from '@angular/material/sort';
import { MatTableModule } from '@angular/material/table';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { catchError, EMPTY, filter, switchMap } from 'rxjs';
import { AuthService } from '../../core/auth.service';
import { AppointmentRow, AppointmentStatus, AppointmentType } from '../../core/models';
import { ReasonDialogComponent } from '../professionals/reason-dialog';
import { AppointmentQuery, AppointmentsService } from './appointments.service';

@Component({
  selector: 'app-appointments-list',
  imports: [
    ReactiveFormsModule, DatePipe, CurrencyPipe, MatTableModule, MatPaginatorModule, MatSortModule,
    MatFormFieldModule, MatInputModule, MatSelectModule, MatButtonModule, MatProgressBarModule, TranslocoPipe,
  ],
  templateUrl: './appointments-list.html',
  styleUrl: './appointments-list.scss',
})
export class AppointmentsListComponent {
  private readonly api = inject(AppointmentsService);
  private readonly auth = inject(AuthService);
  private readonly dialog = inject(MatDialog);
  private readonly snack = inject(MatSnackBar);
  private readonly i18n = inject(TranslocoService);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly columns = ['startAt', 'type', 'patient', 'professional', 'status', 'fee', 'actions'];
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

  protected onPage(e: PageEvent): void {
    this.patch({ page: e.pageIndex, size: e.pageSize });
  }

  protected onSort(s: Sort): void {
    this.patch({ sort: s.direction ? `${s.active},${s.direction}` : undefined, page: 0 });
  }

  protected cancellable(a: AppointmentRow): boolean {
    return this.canCancel() && (a.status === 'REQUESTED' || a.status === 'CONFIRMED');
  }

  protected cancel(a: AppointmentRow): void {
    this.dialog
      .open<ReasonDialogComponent, { titleKey: string }, string>(ReasonDialogComponent, {
        data: { titleKey: 'appointments.cancelTitle' },
      })
      .afterClosed()
      .pipe(
        filter((r): r is string => !!r),
        switchMap((reason) => this.api.cancel(a.id, reason)),
      )
      .subscribe((updated) => {
        this.rows.update((rows) => rows.map((r) => (r.id === updated.id ? updated : r)));
        this.snack.open(this.i18n.translate('appointments.cancelled'), undefined, { duration: 3000 });
      });
  }

  private patch(change: Partial<AppointmentQuery>): void {
    this.query.update((q) => ({ ...q, ...change }));
  }
}
