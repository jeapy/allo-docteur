import { DecimalPipe } from '@angular/common';
import { Component, DestroyRef, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { TranslocoPipe } from '@jsverse/transloco';
import { UiPageHeader, UiStatCard, UiTab, UiTabs } from '../../shared/ui';
import { catchError, EMPTY } from 'rxjs';
import { FinanceSummary } from '../../core/models';
import { FinanceService } from './finance.service';
import { PaymentsTableComponent } from './payments-table';
import { WithdrawalsTableComponent } from './withdrawals-table';

/** Paiements, commissions et retraits (niveaux SUPER et FINANCE). */
@Component({
  selector: 'app-finance',
  imports: [
    DecimalPipe, TranslocoPipe, PaymentsTableComponent, WithdrawalsTableComponent, UiPageHeader, UiStatCard, UiTabs,
  ],
  templateUrl: './finance.html',
})
export class FinanceComponent {
  private readonly api = inject(FinanceService);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly summary = signal<FinanceSummary | null>(null);
  protected readonly tab = signal('payments');
  protected readonly tabs = computed<UiTab[]>(() => [
    { id: 'payments', labelKey: 'finance.tabPayments' },
    { id: 'withdrawals', labelKey: 'finance.tabWithdrawals' },
  ]);

  constructor() {
    this.refresh();
  }

  protected refresh(): void {
    this.api
      .summary()
      .pipe(catchError(() => EMPTY), takeUntilDestroyed(this.destroyRef))
      .subscribe((s) => this.summary.set(s));
  }
}
