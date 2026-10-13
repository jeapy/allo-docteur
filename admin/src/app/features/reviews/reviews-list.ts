import { DatePipe } from '@angular/common';
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
import { ReviewRow, ReviewStatus } from '../../core/models';
import { ReasonDialogComponent } from '../professionals/reason-dialog';
import { ReviewQuery, ReviewsService } from './reviews.service';

/** Modération des avis : un avis masqué disparaît du profil public et de la note moyenne. */
@Component({
  selector: 'app-reviews-list',
  imports: [
    ReactiveFormsModule, DatePipe, MatTableModule, MatPaginatorModule, MatFormFieldModule, MatSelectModule,
    MatButtonModule, MatProgressBarModule, TranslocoPipe,
  ],
  templateUrl: './reviews-list.html',
  styleUrl: './reviews-list.scss',
})
export class ReviewsListComponent {
  private readonly api = inject(ReviewsService);
  private readonly dialog = inject(MatDialog);
  private readonly snack = inject(MatSnackBar);
  private readonly i18n = inject(TranslocoService);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly columns = ['rating', 'comment', 'author', 'status', 'createdAt', 'actions'];
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

  protected onPage(e: PageEvent): void {
    this.query.update((q) => ({ ...q, page: e.pageIndex, size: e.pageSize }));
  }

  protected hide(r: ReviewRow): void {
    this.dialog
      .open<ReasonDialogComponent, { titleKey: string }, string>(ReasonDialogComponent, {
        data: { titleKey: 'reviews.hideTitle' },
      })
      .afterClosed()
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
    this.snack.open(this.i18n.translate(messageKey), undefined, { duration: 3000 });
  }
}
