import { DatePipe, DecimalPipe } from '@angular/common';
import { Component, DestroyRef, computed, inject, output, signal } from '@angular/core';
import { takeUntilDestroyed, toObservable } from '@angular/core/rxjs-interop';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { PageChange, ToastService, UiBadge, UiButton, UiDialog, UiEmptyState, UiPagination, UiSelect, UiSelectField, UiTable, UiTableSkeleton, statusTone } from '../../shared/ui';
import { Observable, catchError, EMPTY, filter, switchMap } from 'rxjs';
import { PayableType, PaymentMethod, PaymentRow, PaymentStatus } from '../../core/models';
import { ReasonDialogComponent } from '../professionals/reason-dialog';
import { FinanceService, PaymentQuery } from './finance.service';

/** Paiements : suivi, confirmation des virements, remboursement des fonds en séquestre. */
@Component({
  selector: 'app-payments-table',
  imports: [
    ReactiveFormsModule, DatePipe, DecimalPipe, TranslocoPipe, UiSelectField, UiSelect, UiTable, UiBadge, UiButton, UiPagination, UiEmptyState, UiTableSkeleton,
  ],
  templateUrl: './payments-table.html',
})
export class PaymentsTableComponent {
  private readonly api = inject(FinanceService);
  private readonly dialog = inject(UiDialog);
  private readonly toast = inject(ToastService);
  private readonly i18n = inject(TranslocoService);
  private readonly destroyRef = inject(DestroyRef);

  /** Émis après une action qui change les indicateurs (pour rafraîchir les tuiles). */
  readonly changed = output<void>();

  protected readonly tone = statusTone;
  protected readonly statuses: PaymentStatus[] = ['PENDING', 'CAPTURED', 'RELEASED', 'REFUNDED', 'FAILED'];
  protected readonly methods: PaymentMethod[] = ['MOBILE_MONEY', 'CARD', 'BANK_TRANSFER', 'WALLET'];
  protected readonly types: PayableType[] = ['APPOINTMENT', 'PHARMACY_ORDER', 'SUBSCRIPTION', 'WALLET_TOPUP'];

  protected readonly status = new FormControl<PaymentStatus | null>(null);
  protected readonly method = new FormControl<PaymentMethod | null>(null);
  protected readonly type = new FormControl<PayableType | null>(null);

  protected readonly query = signal<PaymentQuery>({ page: 0, size: 20 });
  private readonly tick = signal(0);
  private readonly request = computed(() => ({ q: this.query(), n: this.tick() }));
  protected readonly rows = signal<PaymentRow[]>([]);
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
    this.method.valueChanges
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((method) => this.query.update((q) => ({ ...q, method, page: 0 })));
    this.type.valueChanges
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((type) => this.query.update((q) => ({ ...q, type, page: 0 })));
  }

  protected onPage(e: PageChange): void {
    this.query.update((q) => ({ ...q, page: e.pageIndex, size: e.pageSize }));
  }

  protected isPendingTransfer(p: PaymentRow): boolean {
    return p.status === 'PENDING' && p.method === 'BANK_TRANSFER';
  }

  protected confirmTransfer(p: PaymentRow): void {
    this.api.confirmTransfer(p.id).subscribe(() => this.done('finance.transferConfirmed'));
  }

  protected rejectTransfer(p: PaymentRow): void {
    this.askReason('finance.rejectTransferTitle', (reason) => this.api.rejectTransfer(p.id, reason), 'finance.transferRejected');
  }

  protected refund(p: PaymentRow): void {
    this.askReason('finance.refundTitle', (reason) => this.api.refund(p.id, reason), 'finance.refunded');
  }

  private askReason(titleKey: string, action: (reason: string) => Observable<PaymentRow>, doneKey: string): void {
    this.dialog
      .open<string, { titleKey: string }>(ReasonDialogComponent, { titleKey })
      .pipe(
        filter((r): r is string => !!r),
        switchMap((reason) => action(reason)),
      )
      .subscribe(() => this.done(doneKey));
  }

  private done(messageKey: string): void {
    this.tick.update((n) => n + 1);
    this.changed.emit();
    this.toast.success(this.i18n.translate(messageKey));
  }

  private load(q: PaymentQuery) {
    this.loading.set(true);
    return this.api.payments(q).pipe(
      catchError(() => {
        this.loading.set(false);
        return EMPTY;
      }),
    );
  }
}
