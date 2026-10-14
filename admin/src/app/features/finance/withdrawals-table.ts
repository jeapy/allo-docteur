import { DatePipe, DecimalPipe } from '@angular/common';
import { Component, DestroyRef, computed, inject, output, signal } from '@angular/core';
import { takeUntilDestroyed, toObservable } from '@angular/core/rxjs-interop';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { PageChange, ToastService, UiBadge, UiButton, UiDialog, UiEmptyState, UiPagination, UiSelect, UiSelectField, UiTable, UiTableSkeleton, statusTone } from '../../shared/ui';
import { catchError, EMPTY, filter, switchMap } from 'rxjs';
import { WithdrawalRow, WithdrawalStatus } from '../../core/models';
import { ReasonDialogComponent } from '../professionals/reason-dialog';
import { FinanceService, WithdrawalQuery } from './finance.service';

/** Retraits des gains des professionnels : versement ou rejet (le montant est alors restitué au portefeuille). */
@Component({
  selector: 'app-withdrawals-table',
  imports: [
    ReactiveFormsModule, DatePipe, DecimalPipe, TranslocoPipe, UiSelectField, UiSelect, UiTable, UiBadge, UiButton, UiPagination, UiEmptyState, UiTableSkeleton,
  ],
  templateUrl: './withdrawals-table.html',
})
export class WithdrawalsTableComponent {
  private readonly api = inject(FinanceService);
  private readonly dialog = inject(UiDialog);
  private readonly toast = inject(ToastService);
  private readonly i18n = inject(TranslocoService);
  private readonly destroyRef = inject(DestroyRef);

  readonly changed = output<void>();

  protected readonly tone = statusTone;
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

  protected onPage(e: PageChange): void {
    this.query.update((q) => ({ ...q, page: e.pageIndex, size: e.pageSize }));
  }

  protected pay(w: WithdrawalRow): void {
    this.api.payWithdrawal(w.id).subscribe(() => this.done('finance.withdrawalPaid'));
  }

  protected reject(w: WithdrawalRow): void {
    this.dialog
      .open<string, { titleKey: string }>(ReasonDialogComponent, { titleKey: 'finance.rejectWithdrawalTitle' })
      .pipe(
        filter((r): r is string => !!r),
        switchMap((reason) => this.api.rejectWithdrawal(w.id, reason)),
      )
      .subscribe(() => this.done('finance.withdrawalRejected'));
  }

  private done(messageKey: string): void {
    this.tick.update((n) => n + 1);
    this.changed.emit();
    this.toast.success(this.i18n.translate(messageKey));
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
