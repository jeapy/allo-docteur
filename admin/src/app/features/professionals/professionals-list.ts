import { NgIcon } from '@ng-icons/core';
import { DatePipe } from '@angular/common';
import { Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed, toObservable } from '@angular/core/rxjs-interop';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { TranslocoPipe } from '@jsverse/transloco';
import { PageChange, UiBadge, UiButton, UiEmptyState, UiField, UiInput, UiPageHeader, UiPagination, UiSelect, UiSelectField, UiTable, UiTableSkeleton, statusTone } from '../../shared/ui';
import { EMPTY, catchError, debounceTime, distinctUntilChanged, switchMap } from 'rxjs';
import { ProfessionalRow, VerificationStatus } from '../../core/models';
import { ProfessionalQuery, ProfessionalsService } from './professionals.service';

@Component({
  selector: 'app-professionals-list',
  imports: [
    ReactiveFormsModule, DatePipe, RouterLink, TranslocoPipe, NgIcon, UiPageHeader, UiField, UiInput, UiSelectField, UiSelect, UiTable, UiBadge, UiButton, UiPagination, UiEmptyState, UiTableSkeleton,
  ],
  templateUrl: './professionals-list.html',
})
export class ProfessionalsListComponent {
  private readonly api = inject(ProfessionalsService);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly tone = statusTone;
  protected readonly statuses: VerificationStatus[] = ['PENDING', 'APPROVED', 'REJECTED', 'SUSPENDED'];

  protected readonly search = new FormControl('', { nonNullable: true });
  protected readonly status = new FormControl<VerificationStatus | ''>('PENDING', { nonNullable: true });

  protected readonly query = signal<ProfessionalQuery>({ page: 0, size: 20, status: 'PENDING' });
  protected readonly rows = signal<ProfessionalRow[]>([]);
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

    this.search.valueChanges
      .pipe(debounceTime(300), distinctUntilChanged(), takeUntilDestroyed(this.destroyRef))
      .subscribe((q) => this.query.update((s) => ({ ...s, q, page: 0 })));
    this.status.valueChanges
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((status) => this.query.update((s) => ({ ...s, status, page: 0 })));
  }

  protected onPage(e: PageChange): void {
    this.query.update((s) => ({ ...s, page: e.pageIndex, size: e.pageSize }));
  }
}
