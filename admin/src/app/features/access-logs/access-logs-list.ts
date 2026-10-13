import { DatePipe } from '@angular/common';
import { Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed, toObservable } from '@angular/core/rxjs-interop';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSelectModule } from '@angular/material/select';
import { MatTableModule } from '@angular/material/table';
import { TranslocoPipe } from '@jsverse/transloco';
import { catchError, debounceTime, EMPTY, switchMap } from 'rxjs';
import { AccessLogRow, AccessOutcome } from '../../core/models';
import { AccessLogQuery, AccessLogsService } from './access-logs.service';

/** Journal d'accès aux dossiers médicaux (niveau SUPER) : qui, quand, quoi — jamais le contenu. */
@Component({
  selector: 'app-access-logs-list',
  imports: [
    ReactiveFormsModule, DatePipe, MatTableModule, MatPaginatorModule, MatFormFieldModule, MatInputModule,
    MatSelectModule, MatProgressBarModule, TranslocoPipe,
  ],
  templateUrl: './access-logs-list.html',
  styleUrl: './access-logs-list.scss',
})
export class AccessLogsListComponent {
  private readonly api = inject(AccessLogsService);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly columns = ['occurredAt', 'actor', 'action', 'outcome', 'record', 'ip'];
  protected readonly outcomes: AccessOutcome[] = ['GRANTED', 'DENIED'];

  protected readonly outcome = new FormControl<AccessOutcome | ''>('', { nonNullable: true });
  protected readonly recordId = new FormControl('', { nonNullable: true });
  protected readonly actorId = new FormControl('', { nonNullable: true });
  protected readonly from = new FormControl('', { nonNullable: true });
  protected readonly to = new FormControl('', { nonNullable: true });

  protected readonly query = signal<AccessLogQuery>({ page: 0, size: 20 });
  protected readonly rows = signal<AccessLogRow[]>([]);
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

    this.outcome.valueChanges.pipe(takeUntilDestroyed(this.destroyRef)).subscribe((outcome) => this.patch({ outcome, page: 0 }));
    // identifiants saisis à la main : on attend la fin de la frappe, et on ignore les UUID incomplets
    this.recordId.valueChanges
      .pipe(debounceTime(300), takeUntilDestroyed(this.destroyRef))
      .subscribe((v) => this.patch({ recordId: this.uuidOrUndefined(v), page: 0 }));
    this.actorId.valueChanges
      .pipe(debounceTime(300), takeUntilDestroyed(this.destroyRef))
      .subscribe((v) => this.patch({ actorId: this.uuidOrUndefined(v), page: 0 }));
    this.from.valueChanges
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((d) => this.patch({ from: d ? new Date(`${d}T00:00:00`).toISOString() : undefined, page: 0 }));
    this.to.valueChanges.pipe(takeUntilDestroyed(this.destroyRef)).subscribe((d) => {
      const end = d ? new Date(`${d}T00:00:00`) : null;
      if (end) end.setDate(end.getDate() + 1); // borne haute exclusive : toute la journée choisie
      this.patch({ to: end ? end.toISOString() : undefined, page: 0 });
    });
  }

  protected onPage(e: PageEvent): void {
    this.patch({ page: e.pageIndex, size: e.pageSize });
  }

  private uuidOrUndefined(v: string): string | undefined {
    const t = v.trim();
    return /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(t) ? t : undefined;
  }

  private patch(change: Partial<AccessLogQuery>): void {
    this.query.update((q) => ({ ...q, ...change }));
  }
}
