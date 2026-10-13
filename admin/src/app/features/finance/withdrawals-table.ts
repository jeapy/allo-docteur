import { DatePipe, DecimalPipe } from '@angular/common';
import { Component, DestroyRef, computed, inject, output, signal } from '@angular/core';
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
import { catchError, EMPTY, filter, switchMap } from 'rxjs';
import { WithdrawalRow, WithdrawalStatus } from '../../core/models';
import { ReasonDialogComponent } from '../professionals/reason-dialog';
import { FinanceService, WithdrawalQuery } from './finance.service';

/** Retraits des gains des professionnels : versement ou rejet (le montant est alors restitué au portefeuille). */
@Component({
  selector: 'app-withdrawals-table',
  imports: [
    ReactiveFormsModule, DatePipe, DecimalPipe, MatTableModule, MatPaginatorModule, MatFormFieldModule,
    MatSelectModule, MatButtonModule, MatProgressBarModule, TranslocoPipe,
  ],
  templateUrl: './withdrawals-table.html',
  styleUrl: './finance.scss',
})
export class WithdrawalsTableComponent {
  private readonly api = inject(FinanceService);
  private readonly dialog = inject(MatDialog);
  private readonly snack = inject(MatSnackBar);
  private readonly i18n = inject(TranslocoService);
  private readonly destroyRef = inject(DestroyRef);

  readonly changed = output<void>();

  protected readonly columns = ['createdAt', 'user', 'method', 'destination', 'amount', 'status', 'actions'];
  protected readonly statuses: WithdrawalStatus[] = ['REQUESTED', 'PAID', 'REJECTED'];
  protected readonly status = new FormControl<WithdrawalStatus | null>('REQUESTED');

  protected readonly query = signal<WithdrawalQuery>({ page: 0, size: 20, status: 'REQUESTED' });
  private readonly tick = signal(0);
  private readonly request = computed(() => ({ q: this.query(), n: this.tick() }));
  protected readonly rows = signal<WithdrawalRow[]>([]);
  protected readonly total = signal(0);
  protected readonly loading = signal(false);

  constructor() {
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

    this.status.valueChanges
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((status) => this.query.update((q) => ({ ...q, status, page: 0 })));
  }

  protected onPage(e: PageEvent): void {
    this.query.update((q) => ({ ...q, page: e.pageIndex, size: e.pageSize }));
  }

  protected pay(w: WithdrawalRow): void {
    this.api.payWithdrawal(w.id).subscribe(() => this.done('finance.withdrawalPaid'));
  }

  protected reject(w: WithdrawalRow): void {
    this.dialog
      .open<ReasonDialogComponent, { titleKey: string }, string>(ReasonDialogComponent, {
        data: { titleKey: 'finance.rejectWithdrawalTitle' },
      })
      .afterClosed()
      .pipe(
        filter((r): r is string => !!r),
        switchMap((reason) => this.api.rejectWithdrawal(w.id, reason)),
      )
      .subscribe(() => this.done('finance.withdrawalRejected'));
  }

  private done(messageKey: string): void {
    this.tick.update((n) => n + 1);
    this.changed.emit();
    this.snack.open(this.i18n.translate(messageKey), undefined, { duration: 3000 });
  }

  private load(q: WithdrawalQuery) {
    this.loading.set(true);
    return this.api.withdrawals(q).pipe(
      catchError(() => {
        this.loading.set(false);
        return EMPTY;
      }),
    );
  }
}
