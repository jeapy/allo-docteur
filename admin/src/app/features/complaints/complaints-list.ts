import { DatePipe } from '@angular/common';
import { Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed, toObservable } from '@angular/core/rxjs-interop';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { TranslocoPipe } from '@jsverse/transloco';
import { PageChange, statusTone, UiBadge, UiButton, UiEmptyState, UiPageHeader, UiPagination, UiSelect, UiSelectField, UiTable, UiTableSkeleton } from '../../shared/ui';
import { catchError, EMPTY, switchMap } from 'rxjs';
import { ComplaintRow, ComplaintStatus, ComplaintSummary } from '../../core/models';
import { ComplaintQuery, ComplaintsService } from './complaints.service';

/** File des réclamations, les plus anciennes ouvertes d'abord quand on filtre sur un statut. */
@Component({
  selector: 'app-complaints-list',
  imports: [
    ReactiveFormsModule, DatePipe, RouterLink, TranslocoPipe, UiPageHeader, UiSelectField, UiSelect, UiTable, UiBadge, UiButton, UiPagination, UiEmptyState, UiTableSkeleton,
  ],
  templateUrl: './complaints-list.html',
})
export class ComplaintsListComponent {
  private readonly api = inject(ComplaintsService);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly tone = statusTone;
  protected readonly statuses: ComplaintStatus[] = ['OPEN', 'IN_REVIEW', 'RESOLVED', 'REJECTED'];
  protected readonly status = new FormControl<ComplaintStatus | null>(null);

  protected readonly query = signal<ComplaintQuery>({ page: 0, size: 20 });
  protected readonly rows = signal<ComplaintRow[]>([]);
  protected readonly total = signal(0);
  protected readonly loading = signal(false);
  protected readonly summary = signal<ComplaintSummary | null>(null);

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

    this.api.summary().pipe(catchError(() => EMPTY), takeUntilDestroyed(this.destroyRef)).subscribe((s) => this.summary.set(s));

    this.status.valueChanges
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((status) => this.query.update((q) => ({ ...q, status, page: 0 })));
  }

  protected onPage(e: PageChange): void {
    this.query.update((q) => ({ ...q, page: e.pageIndex, size: e.pageSize }));
  }
}
