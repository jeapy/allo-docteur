import { DatePipe, DecimalPipe } from '@angular/common';
import { Component, DestroyRef, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed, toObservable } from '@angular/core/rxjs-interop';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { PageChange, ToastService, UiBadge, UiButton, UiDialog, UiEmptyState, UiPageHeader, UiPagination, UiSelect, UiSelectField, UiTab, UiTable, UiTableSkeleton, UiTabs, statusTone } from '../../shared/ui';
import { catchError, EMPTY, filter, switchMap } from 'rxjs';
import { AuthService } from '../../core/auth.service';
import { PlanPayload, PlanRow, SubscriptionRow, SubscriptionStatus } from '../../core/models';
import { PlanFormDialogComponent } from './plan-form-dialog';
import { SubscriptionQuery, SubscriptionsService } from './subscriptions.service';

/** Abonnements en cours et gestion des formules (modification réservée aux niveaux SUPER et FINANCE). */
@Component({
  selector: 'app-subscriptions-list',
  imports: [
    ReactiveFormsModule, DatePipe, DecimalPipe, TranslocoPipe, UiPageHeader, UiSelectField, UiSelect, UiTable, UiBadge, UiButton, UiPagination, UiEmptyState, UiTableSkeleton, UiTabs,
  ],
  templateUrl: './subscriptions-list.html',
})
export class SubscriptionsListComponent {
  private readonly api = inject(SubscriptionsService);
  private readonly auth = inject(AuthService);
  private readonly dialog = inject(UiDialog);
  private readonly toast = inject(ToastService);
  private readonly i18n = inject(TranslocoService);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly tone = statusTone;
  protected readonly tab = signal('subscriptions');
  protected readonly tabs = computed<UiTab[]>(() => [
    { id: 'subscriptions', labelKey: 'subscriptions.tabSubscriptions' },
    { id: 'plans', labelKey: 'subscriptions.tabPlans' },
  ]);
  protected readonly statuses: SubscriptionStatus[] = ['TRIAL', 'ACTIVE', 'EXPIRED', 'CANCELLED'];
  protected readonly status = new FormControl<SubscriptionStatus | null>(null);

  protected readonly query = signal<SubscriptionQuery>({ page: 0, size: 20 });
  protected readonly rows = signal<SubscriptionRow[]>([]);
  protected readonly total = signal(0);
  protected readonly loading = signal(false);
  protected readonly plans = signal<PlanRow[]>([]);
  private readonly plansTick = signal(0);

  protected readonly canEditPlans = computed(() => ['SUPER', 'FINANCE'].includes(this.auth.user()?.adminLevel ?? ''));

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

    toObservable(this.plansTick)
      .pipe(
        switchMap(() => this.api.plans().pipe(catchError(() => EMPTY))),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe((p) => this.plans.set(p));

    this.status.valueChanges
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((status) => this.query.update((q) => ({ ...q, status, page: 0 })));
  }

  protected onPage(e: PageChange): void {
    this.query.update((q) => ({ ...q, page: e.pageIndex, size: e.pageSize }));
  }

  protected edit(plan: PlanRow): void {
    this.dialog
      .open<PlanPayload, PlanRow>(PlanFormDialogComponent, plan)
      .pipe(
        filter((p): p is PlanPayload => !!p),
        switchMap((payload) => this.api.updatePlan(plan.id, payload)),
      )
      .subscribe(() => {
        this.plansTick.update((n) => n + 1);
        this.toast.success(this.i18n.translate('subscriptions.planSaved'));
      });
  }
}
