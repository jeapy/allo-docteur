import { Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed, toObservable } from '@angular/core/rxjs-interop';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { PageChange, ToastService, UiBadge, UiButton, UiDialog, UiEmptyState, UiPageHeader, UiPagination, UiSelect, UiSelectField, UiTable, UiTableSkeleton, statusTone } from '../../shared/ui';
import { catchError, EMPTY, filter, switchMap } from 'rxjs';
import { PartnerPayload, PartnerRow, PartnerStatus, PartnerType } from '../../core/models';
import { PARTNER_TYPES, PartnerFormDialogComponent } from './partner-form-dialog';
import { PartnerQuery, PartnersService } from './partners.service';

/** Annuaire des partenaires : cliniques, laboratoires, assureurs, mutuelles… */
@Component({
  selector: 'app-partners-list',
  imports: [
    ReactiveFormsModule, TranslocoPipe, UiPageHeader, UiSelectField, UiSelect, UiTable, UiBadge, UiButton, UiPagination, UiEmptyState, UiTableSkeleton,
  ],
  templateUrl: './partners-list.html',
})
export class PartnersListComponent {
  private readonly api = inject(PartnersService);
  private readonly dialog = inject(UiDialog);
  private readonly toast = inject(ToastService);
  private readonly i18n = inject(TranslocoService);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly tone = statusTone;
  protected readonly types = PARTNER_TYPES;
  protected readonly statuses: PartnerStatus[] = ['ACTIVE', 'INACTIVE'];
  protected readonly type = new FormControl<PartnerType | null>(null);
  protected readonly status = new FormControl<PartnerStatus | null>(null);

  protected readonly query = signal<PartnerQuery>({ page: 0, size: 20 });
  protected readonly rows = signal<PartnerRow[]>([]);
  protected readonly total = signal(0);
  protected readonly loading = signal(false);

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

    this.type.valueChanges
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((type) => this.query.update((q) => ({ ...q, type, page: 0 })));
    this.status.valueChanges
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((status) => this.query.update((q) => ({ ...q, status, page: 0 })));
  }

  protected onPage(e: PageChange): void {
    this.query.update((q) => ({ ...q, page: e.pageIndex, size: e.pageSize }));
  }

  protected create(): void {
    this.openForm(null)
      .pipe(switchMap((payload) => this.api.create(payload)))
      .subscribe(() => this.saved());
  }

  protected edit(p: PartnerRow): void {
    this.openForm(p)
      .pipe(switchMap((payload) => this.api.update(p.id, payload)))
      .subscribe(() => this.saved());
  }

  protected toggle(p: PartnerRow): void {
    this.api.setActive(p.id, p.status !== 'ACTIVE').subscribe(() => this.saved());
  }

  private openForm(existing: PartnerRow | null) {
    return this.dialog
      .open<PartnerPayload, PartnerRow | null>(PartnerFormDialogComponent, existing)
      .pipe(filter((p): p is PartnerPayload => !!p));
  }

  private saved(): void {
    this.query.update((q) => ({ ...q })); // nouvelle référence : recharge la page courante
    this.toast.success(this.i18n.translate('partners.saved'));
  }
}
