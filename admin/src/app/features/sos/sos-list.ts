import { DatePipe } from '@angular/common';
import { Component, DestroyRef, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed, toObservable } from '@angular/core/rxjs-interop';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSelectModule } from '@angular/material/select';
import { MatSnackBar } from '@angular/material/snack-bar';
import { MatTableModule } from '@angular/material/table';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { catchError, EMPTY, filter, interval, switchMap } from 'rxjs';
import { AuthService } from '../../core/auth.service';
import { SosAlertRow, SosStatus, SosSummary } from '../../core/models';
import { ReasonDialogComponent } from '../professionals/reason-dialog';
import { SosQuery, SosService } from './sos.service';

/** Suivi des urgences en cours. Rafraîchi toutes les 30 s : une alerte non prise en charge doit se voir vite. */
@Component({
  selector: 'app-sos-list',
  imports: [
    ReactiveFormsModule, DatePipe, MatTableModule, MatPaginatorModule, MatFormFieldModule, MatSelectModule,
    MatButtonModule, MatProgressBarModule, TranslocoPipe,
  ],
  templateUrl: './sos-list.html',
  styleUrl: './sos-list.scss',
})
export class SosListComponent {
  private readonly api = inject(SosService);
  private readonly auth = inject(AuthService);
  private readonly dialog = inject(MatDialog);
  private readonly snack = inject(MatSnackBar);
  private readonly i18n = inject(TranslocoService);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly columns = ['createdAt', 'category', 'patient', 'city', 'professional', 'status', 'actions'];
  protected readonly statuses: SosStatus[] = ['OPEN', 'ACCEPTED', 'RESOLVED', 'CANCELLED'];
  protected readonly status = new FormControl<SosStatus[]>(['OPEN', 'ACCEPTED'], { nonNullable: true });

  protected readonly query = signal<SosQuery>({ page: 0, size: 20, status: ['OPEN', 'ACCEPTED'] });
  protected readonly rows = signal<SosAlertRow[]>([]);
  protected readonly total = signal(0);
  protected readonly loading = signal(false);
  protected readonly summary = signal<SosSummary>({ open: 0, accepted: 0 });
  private readonly tick = signal(0);
  /** Une nouvelle requête part quand les filtres changent ou à chaque rafraîchissement automatique. */
  private readonly request = computed(() => ({ q: this.query(), n: this.tick() }));

  protected readonly canCancel = () => ['SUPER', 'SUPPORT'].includes(this.auth.user()?.adminLevel ?? '');

  constructor() {
    interval(30_000).pipe(takeUntilDestroyed(this.destroyRef)).subscribe(() => this.tick.update((n) => n + 1));

    toObservable(this.request)
      .pipe(
        switchMap(({ q }) => this.load(q)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe((p) => {
        this.rows.set(p.content);
        this.total.set(p.totalElements);
        this.loading.set(false);
      });

    toObservable(this.tick)
      .pipe(
        switchMap(() => this.api.summary().pipe(catchError(() => EMPTY))),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe((s) => this.summary.set(s));

    this.status.valueChanges
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((status) => this.query.update((q) => ({ ...q, status, page: 0 })));
  }

  protected onPage(e: PageEvent): void {
    this.query.update((q) => ({ ...q, page: e.pageIndex, size: e.pageSize }));
  }

  protected cancellable(a: SosAlertRow): boolean {
    return this.canCancel() && (a.status === 'OPEN' || a.status === 'ACCEPTED');
  }

  protected cancel(a: SosAlertRow): void {
    this.dialog
      .open<ReasonDialogComponent, { titleKey: string }, string>(ReasonDialogComponent, {
        data: { titleKey: 'sos.cancelTitle' },
      })
      .afterClosed()
      .pipe(
        filter((r): r is string => !!r),
        switchMap((reason) => this.api.cancel(a.id, reason)),
      )
      .subscribe(() => {
        this.tick.update((n) => n + 1);
        this.snack.open(this.i18n.translate('sos.cancelled'), undefined, { duration: 3000 });
      });
  }

  private load(q: SosQuery) {
    this.loading.set(true);
    return this.api.list(q).pipe(
      catchError(() => {
        this.loading.set(false);
        return EMPTY;
      }),
    );
  }
}
