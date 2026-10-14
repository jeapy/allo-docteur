import { DatePipe, DecimalPipe } from '@angular/common';
import { Component, DestroyRef, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { TranslocoPipe } from '@jsverse/transloco';
import { OperatorFunction, catchError, of } from 'rxjs';
import { AuthService } from '../../core/auth.service';
import { AppointmentRow, ComplaintSummary, FinanceSummary, SosSummary } from '../../core/models';
import {
  BadgeTone, BarDatum, UiBadge, UiBarChart, UiButton, UiCard, UiEmptyState, UiPageHeader, UiSkeleton, UiStatCard, UiTable,
} from '../../shared/ui';
import { AppointmentsService } from '../appointments/appointments.service';
import { ComplaintsService } from '../complaints/complaints.service';
import { FinanceService } from '../finance/finance.service';
import { SosService } from '../sos/sos.service';
import { UsersService } from '../users/users.service';

/** Une erreur HTTP (ex. 403 selon le niveau d'admin) donne `null` : le bloc se masque sans casser la page. */
function quiet<T>(): OperatorFunction<T, T | null> {
  return catchError(() => of(null));
}

const STATUS_TONE: Record<string, BadgeTone> = {
  REQUESTED: 'warning', CONFIRMED: 'info', IN_PROGRESS: 'primary', COMPLETED: 'success', CANCELLED: 'neutral', NO_SHOW: 'danger',
};

/**
 * Tableau de bord : on compose uniquement des services existants (aucune nouvelle API). Chaque bloc se charge
 * indépendamment et se dégrade sans bruit si le niveau d'administration n'y a pas accès (ex. finance).
 */
@Component({
  selector: 'app-dashboard',
  imports: [
    DatePipe, DecimalPipe, RouterLink, TranslocoPipe, UiBadge, UiBarChart, UiButton, UiCard, UiEmptyState,
    UiPageHeader, UiSkeleton, UiStatCard, UiTable,
  ],
  templateUrl: './dashboard.html',
})
export class DashboardComponent {
  private readonly destroyRef = inject(DestroyRef);
  private readonly auth = inject(AuthService);

  protected readonly canSeeFinance = ['SUPER', 'FINANCE'].includes(this.auth.user()?.adminLevel ?? '');
  protected readonly canSeeComplaints = ['SUPER', 'SUPPORT'].includes(this.auth.user()?.adminLevel ?? '');

  protected readonly users = signal<number | null>(null);
  protected readonly sos = signal<SosSummary | null>(null);
  protected readonly complaints = signal<ComplaintSummary | null>(null);
  protected readonly finance = signal<FinanceSummary | null>(null);
  protected readonly recent = signal<AppointmentRow[] | null>(null);
  protected readonly todayCount = signal<number | null>(null);

  protected readonly openComplaints = computed(() => {
    const c = this.complaints();
    return c ? c.OPEN + c.IN_REVIEW : null;
  });

  protected readonly complaintBars = computed<BarDatum[]>(() => {
    const c = this.complaints();
    if (!c) return [];
    return [
      { label: 'complaintStatuses.OPEN', value: c.OPEN, color: 'var(--ds-warning)' },
      { label: 'complaintStatuses.IN_REVIEW', value: c.IN_REVIEW, color: 'var(--ds-info)' },
      { label: 'complaintStatuses.RESOLVED', value: c.RESOLVED, color: 'var(--ds-success)' },
      { label: 'complaintStatuses.REJECTED', value: c.REJECTED, color: 'var(--ds-text-muted)' },
    ];
  });

  protected readonly financeBars = computed<BarDatum[]>(() => {
    const f = this.finance();
    if (!f) return [];
    return [
      { label: 'dashboard.fin.commissions', value: f.commissions, color: 'var(--ds-primary)' },
      { label: 'dashboard.fin.subscriptionRevenue', value: f.subscriptionRevenue, color: 'var(--ds-secondary)' },
      { label: 'dashboard.fin.escrow', value: f.escrow, color: 'var(--ds-info)' },
      { label: 'dashboard.fin.refunded', value: f.refunded, color: 'var(--ds-danger)' },
    ];
  });

  constructor() {
    const start = new Date();
    start.setHours(0, 0, 0, 0);
    const end = new Date(start);
    end.setDate(end.getDate() + 1);

    inject(UsersService).list({ page: 0, size: 1 }).pipe(quiet(), takeUntilDestroyed(this.destroyRef))
      .subscribe((p) => this.users.set(p?.totalElements ?? 0));
    inject(SosService).summary().pipe(quiet(), takeUntilDestroyed(this.destroyRef))
      .subscribe((s) => this.sos.set(s ?? { open: 0, accepted: 0 }));
    inject(AppointmentsService)
      .list({ page: 0, size: 1, from: start.toISOString(), to: end.toISOString() })
      .pipe(quiet(), takeUntilDestroyed(this.destroyRef))
      .subscribe((p) => this.todayCount.set(p?.totalElements ?? 0));
    inject(AppointmentsService)
      .list({ page: 0, size: 6, sort: 'startAt,desc' })
      .pipe(quiet(), takeUntilDestroyed(this.destroyRef))
      .subscribe((p) => this.recent.set(p?.content ?? []));

    if (this.canSeeComplaints) {
      inject(ComplaintsService).summary().pipe(quiet(), takeUntilDestroyed(this.destroyRef))
        .subscribe((c) => this.complaints.set(c ?? { OPEN: 0, IN_REVIEW: 0, RESOLVED: 0, REJECTED: 0 }));
    }
    if (this.canSeeFinance) {
      inject(FinanceService).summary().pipe(quiet(), takeUntilDestroyed(this.destroyRef))
        .subscribe((f) => this.finance.set(f));
    }
  }

  protected tone(status: string): BadgeTone {
    return STATUS_TONE[status] ?? 'neutral';
  }
}
