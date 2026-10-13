import { DecimalPipe } from '@angular/common';
import { Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { MatTabsModule } from '@angular/material/tabs';
import { TranslocoPipe } from '@jsverse/transloco';
import { catchError, EMPTY } from 'rxjs';
import { FinanceSummary } from '../../core/models';
import { FinanceService } from './finance.service';
import { PaymentsTableComponent } from './payments-table';
import { WithdrawalsTableComponent } from './withdrawals-table';

/** Paiements, commissions et retraits (niveaux SUPER et FINANCE). */
@Component({
  selector: 'app-finance',
  imports: [DecimalPipe, MatTabsModule, TranslocoPipe, PaymentsTableComponent, WithdrawalsTableComponent],
  templateUrl: './finance.html',
  styleUrl: './finance.scss',
})
export class FinanceComponent {
  private readonly api = inject(FinanceService);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly summary = signal<FinanceSummary | null>(null);

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
