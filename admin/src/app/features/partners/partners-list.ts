import { Component, DestroyRef, inject, signal } from '@angular/core';
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
import { PartnerPayload, PartnerRow, PartnerStatus, PartnerType } from '../../core/models';
import { PARTNER_TYPES, PartnerFormDialogComponent } from './partner-form-dialog';
import { PartnerQuery, PartnersService } from './partners.service';

/** Annuaire des partenaires : cliniques, laboratoires, assureurs, mutuelles… */
@Component({
  selector: 'app-partners-list',
  imports: [
    ReactiveFormsModule, MatTableModule, MatPaginatorModule, MatFormFieldModule, MatSelectModule, MatButtonModule,
    MatProgressBarModule, TranslocoPipe,
  ],
  templateUrl: './partners-list.html',
  styleUrl: './partners-list.scss',
})
export class PartnersListComponent {
  private readonly api = inject(PartnersService);
  private readonly dialog = inject(MatDialog);
  private readonly snack = inject(MatSnackBar);
  private readonly i18n = inject(TranslocoService);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly columns = ['name', 'type', 'city', 'contact', 'status', 'actions'];
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

  protected onPage(e: PageEvent): void {
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
      .open<PartnerFormDialogComponent, PartnerRow | null, PartnerPayload>(PartnerFormDialogComponent, { data: existing })
      .afterClosed()
      .pipe(filter((p): p is PartnerPayload => !!p));
  }

  private saved(): void {
    this.query.update((q) => ({ ...q })); // nouvelle référence : recharge la page courante
    this.snack.open(this.i18n.translate('partners.saved'), undefined, { duration: 3000 });
  }
}
