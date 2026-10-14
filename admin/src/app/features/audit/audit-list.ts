import { DatePipe } from '@angular/common';
import { Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed, toObservable } from '@angular/core/rxjs-interop';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { TranslocoPipe } from '@jsverse/transloco';
import { catchError, debounceTime, EMPTY, switchMap } from 'rxjs';
import { AuditRow } from '../../core/models';
import { PageChange, UiEmptyState, UiField, UiInput, UiPageHeader, UiPagination, UiSelect, UiSelectField, UiTable, UiTableSkeleton } from '../../shared/ui';
import { AuditQuery, AuditService } from './audit.service';

/** Journal d'audit général (niveau SUPER) : actions d'administration et événements de sécurité. */
@Component({
  selector: 'app-audit-list',
  imports: [ReactiveFormsModule, DatePipe, TranslocoPipe, UiPageHeader, UiField, UiInput, UiSelectField, UiSelect, UiTable, UiPagination, UiEmptyState, UiTableSkeleton],
  templateUrl: './audit-list.html',
})
export class AuditListComponent {
  private readonly api = inject(AuditService);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly actions = signal<string[]>([]);
  protected readonly action = new FormControl('', { nonNullable: true });
  protected readonly targetId = new FormControl('', { nonNullable: true });
  protected readonly actorId = new FormControl('', { nonNullable: true });
  protected readonly from = new FormControl('', { nonNullable: true });
  protected readonly to = new FormControl('', { nonNullable: true });

  protected readonly query = signal<AuditQuery>({ page: 0, size: 20 });
  protected readonly rows = signal<AuditRow[]>([]);
  protected readonly total = signal(0);
  protected readonly loading = signal(false);

  constructor() {
    this.api.actions().pipe(takeUntilDestroyed(this.destroyRef)).subscribe((a) => this.actions.set(a));

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

    this.action.valueChanges.pipe(takeUntilDestroyed(this.destroyRef)).subscribe((action) => this.patch({ action, page: 0 }));
    this.targetId.valueChanges
      .pipe(debounceTime(300), takeUntilDestroyed(this.destroyRef))
      .subscribe((v) => this.patch({ targetId: v.trim() || undefined, page: 0 }));
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

  protected onPage(e: PageChange): void {
    this.patch({ page: e.pageIndex, size: e.pageSize });
  }

  private uuidOrUndefined(v: string): string | undefined {
    const t = v.trim();
    return /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(t) ? t : undefined;
  }

  private patch(change: Partial<AuditQuery>): void {
    this.query.update((q) => ({ ...q, ...change }));
  }
}
