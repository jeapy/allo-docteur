import { DatePipe } from '@angular/common';
import { Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed, toObservable } from '@angular/core/rxjs-interop';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { PageChange, SortChange, ToastService, UiBadge, UiButton, UiEmptyState, UiField, UiInput, UiPageHeader, UiPagination, UiSelect, UiSelectField, UiSortHeader, UiTable, UiTableSkeleton, statusTone } from '../../shared/ui';
import { catchError, debounceTime, distinctUntilChanged, EMPTY, switchMap } from 'rxjs';
import { AuthService } from '../../core/auth.service';
import { Role, UserStatus, UserSummary } from '../../core/models';
import { UserQuery, UsersService } from './users.service';

@Component({
  selector: 'app-users-list',
  imports: [
    ReactiveFormsModule, DatePipe, TranslocoPipe, UiPageHeader, UiField, UiInput, UiSelectField, UiSelect, UiTable, UiSortHeader, UiBadge, UiButton, UiPagination, UiEmptyState, UiTableSkeleton,
  ],
  templateUrl: './users-list.html',
})
export class UsersListComponent {
  private readonly api = inject(UsersService);
  private readonly auth = inject(AuthService);
  private readonly toast = inject(ToastService);
  private readonly i18n = inject(TranslocoService);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly tone = statusTone;
  protected readonly roles: Role[] = ['PATIENT', 'PROFESSIONAL', 'ADMIN'];
  protected readonly statuses: UserStatus[] = ['ACTIVE', 'SUSPENDED', 'PENDING'];

  protected readonly search = new FormControl('', { nonNullable: true });
  protected readonly role = new FormControl<Role | ''>('', { nonNullable: true });
  protected readonly status = new FormControl<UserStatus | ''>('', { nonNullable: true });

  protected readonly query = signal<UserQuery>({ page: 0, size: 20, sort: 'createdAt,desc' });
  protected readonly rows = signal<UserSummary[]>([]);
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
      .subscribe((q) => this.patch({ q, page: 0 }));
    this.role.valueChanges.pipe(takeUntilDestroyed(this.destroyRef)).subscribe((role) => this.patch({ role, page: 0 }));
    this.status.valueChanges
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((status) => this.patch({ status, page: 0 }));
  }

  protected onPage(e: PageChange): void {
    this.patch({ page: e.pageIndex, size: e.pageSize });
  }

  protected onSort(s: SortChange): void {
    this.patch({ sort: s.direction ? `${s.active},${s.direction}` : undefined, page: 0 });
  }

  protected readonly canManage = () => ['SUPER', 'SUPPORT'].includes(this.auth.user()?.adminLevel ?? '');

  protected sendReset(u: UserSummary): void {
    this.api.sendPasswordReset(u.id).subscribe(() => this.toast.success(this.i18n.translate('users.resetSent', { email: u.email })));
  }

  protected toggle(u: UserSummary): void {
    const next: UserStatus = u.status === 'SUSPENDED' ? 'ACTIVE' : 'SUSPENDED';
    this.api.setStatus(u.id, next).subscribe((updated) =>
      this.rows.update((rows) => rows.map((r) => (r.id === updated.id ? updated : r))),
    );
  }

  private patch(change: Partial<UserQuery>): void {
    this.query.update((q) => ({ ...q, ...change }));
  }
}
