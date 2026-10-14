import { DatePipe } from '@angular/common';
import { Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed, toObservable } from '@angular/core/rxjs-interop';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { NgIcon } from '@ng-icons/core';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { PageChange, ToastService, UiBadge, UiButton, UiDialog, UiEmptyState, UiPageHeader, UiPagination, UiSelect, UiSelectField, UiTable, UiTableSkeleton, statusTone } from '../../shared/ui';
import { catchError, EMPTY, filter, switchMap } from 'rxjs';
import { ReviewRow, ReviewStatus } from '../../core/models';
import { ReasonDialogComponent } from '../professionals/reason-dialog';
import { ReviewQuery, ReviewsService } from './reviews.service';

/** Modération des avis : un avis masqué disparaît du profil public et de la note moyenne. */
@Component({
  selector: 'app-reviews-list',
  imports: [
    ReactiveFormsModule, DatePipe, TranslocoPipe, NgIcon, UiPageHeader, UiSelectField, UiSelect, UiTable, UiBadge, UiButton, UiPagination, UiEmptyState, UiTableSkeleton,
  ],
  templateUrl: './reviews-list.html',
})
export class ReviewsListComponent {
  private readonly api = inject(ReviewsService);
  private readonly dialog = inject(UiDialog);
  private readonly toast = inject(ToastService);
  private readonly i18n = inject(TranslocoService);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly tone = statusTone;
  protected readonly statuses: ReviewStatus[] = ['VISIBLE', 'HIDDEN'];
  protected readonly status = new FormControl<ReviewStatus | null>(null);

  protected readonly query = signal<ReviewQuery>({ page: 0, size: 20 });
  protected readonly rows = signal<ReviewRow[]>([]);
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

    this.status.valueChanges
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((status) => this.query.update((q) => ({ ...q, status, page: 0 })));
  }

  protected onPage(e: PageChange): void {
    this.query.update((q) => ({ ...q, page: e.pageIndex, size: e.pageSize }));
  }

  protected hide(r: ReviewRow): void {
    this.dialog
      .open<string, { titleKey: string }>(ReasonDialogComponent, { titleKey: 'reviews.hideTitle' })
      .pipe(
        filter((x): x is string => !!x),
        switchMap((reason) => this.api.hide(r.id, reason)),
      )
      .subscribe(() => this.reload('reviews.hidden'));
  }

  protected show(r: ReviewRow): void {
    this.api.show(r.id).subscribe(() => this.reload('reviews.restored'));
  }

  private reload(messageKey: string): void {
    this.query.update((q) => ({ ...q })); // nouvelle référence : recharge la page courante
    this.toast.success(this.i18n.translate(messageKey));
  }
}
