import { Component, computed, input, output } from '@angular/core';
import { NgIcon } from '@ng-icons/core';
import { TranslocoPipe } from '@jsverse/transloco';
import { UiButton } from './button';

export interface PageChange {
  pageIndex: number;
  pageSize: number;
}

/** Pagination côté serveur. Émet la même forme que `PageEvent` (pageIndex, pageSize). */
@Component({
  selector: 'ui-pagination',
  imports: [NgIcon, UiButton, TranslocoPipe],
  template: `
    <nav class="flex flex-wrap items-center justify-between gap-3 border-t border-border px-4 py-3 text-sm text-muted"
         [attr.aria-label]="'ui.pagination.label' | transloco">
      <div class="flex items-center gap-2">
        <label for="ui-page-size">{{ 'ui.pagination.perPage' | transloco }}</label>
        <select id="ui-page-size"
          class="h-8 rounded-sm border border-border-strong bg-surface px-2 text-text transition-colors duration-150 hover:bg-surface-muted"
          (change)="changeSize($any($event.target).value)">
          @for (o of pageSizeOptions(); track o) { <option [value]="o" [selected]="o === pageSize()">{{ o }}</option> }
        </select>
      </div>
      <span aria-live="polite">{{ 'ui.pagination.range' | transloco: { from: from(), to: to(), total: length() } }}</span>
      <div class="flex items-center gap-1">
        <button uiBtn variant="ghost" size="icon" type="button" [disabled]="pageIndex() === 0"
          [attr.aria-label]="'ui.pagination.previous' | transloco" (click)="go(pageIndex() - 1)">
          <ng-icon name="lucideChevronLeft" size="18" aria-hidden="true" />
        </button>
        <button uiBtn variant="ghost" size="icon" type="button" [disabled]="pageIndex() + 1 >= pages()"
          [attr.aria-label]="'ui.pagination.next' | transloco" (click)="go(pageIndex() + 1)">
          <ng-icon name="lucideChevronRight" size="18" aria-hidden="true" />
        </button>
      </div>
    </nav>
  `,
  host: { class: 'block' },
})
export class UiPagination {
  readonly length = input.required<number>();
  readonly pageIndex = input.required<number>();
  readonly pageSize = input.required<number>();
  readonly pageSizeOptions = input<number[]>([10, 20, 50]);
  readonly page = output<PageChange>();

  protected readonly pages = computed(() => Math.max(1, Math.ceil(this.length() / this.pageSize())));
  protected readonly from = computed(() => (this.length() === 0 ? 0 : this.pageIndex() * this.pageSize() + 1));
  protected readonly to = computed(() => Math.min(this.length(), (this.pageIndex() + 1) * this.pageSize()));

  protected go(index: number): void {
    this.page.emit({ pageIndex: index, pageSize: this.pageSize() });
  }

  protected changeSize(size: string): void {
    this.page.emit({ pageIndex: 0, pageSize: Number(size) });
  }
}
