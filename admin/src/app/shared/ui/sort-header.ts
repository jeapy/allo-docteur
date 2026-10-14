import { Component, computed, input, output } from '@angular/core';
import { NgIcon } from '@ng-icons/core';

export interface SortChange {
  active: string;
  direction: 'asc' | 'desc' | '';
}

/**
 * En-tête de colonne triable côté serveur : `<th uiSortHeader="createdAt" [sort]="query().sort" (sortChange)="onSort($event)">`.
 * Cycle : ascendant → descendant → aucun tri. `sort` est la valeur Spring (`champ,asc`).
 */
@Component({
  selector: 'th[uiSortHeader]',
  imports: [NgIcon],
  template: `
    <button type="button" class="inline-flex cursor-pointer items-center gap-1 border-0 bg-transparent p-0 font-[inherit] uppercase tracking-[inherit] text-inherit
        transition-colors duration-150 hover:text-text" (click)="toggle()">
      <ng-content />
      <ng-icon [name]="icon()" size="14" aria-hidden="true" [class.opacity-40]="!direction()" />
    </button>
  `,
  host: { '[attr.aria-sort]': 'ariaSort()' },
})
export class UiSortHeader {
  readonly uiSortHeader = input.required<string>();
  readonly sort = input<string | undefined>();
  readonly sortChange = output<SortChange>();

  protected readonly direction = computed<'asc' | 'desc' | ''>(() => {
    const [field, dir] = (this.sort() ?? '').split(',');
    return field === this.uiSortHeader() && (dir === 'asc' || dir === 'desc') ? dir : '';
  });
  protected readonly icon = computed(() =>
    this.direction() === 'asc' ? 'lucideArrowUp' : this.direction() === 'desc' ? 'lucideArrowDown' : 'lucideChevronsUpDown',
  );
  protected readonly ariaSort = computed(() =>
    ({ asc: 'ascending', desc: 'descending', '': 'none' })[this.direction()],
  );

  protected toggle(): void {
    const next = this.direction() === '' ? 'asc' : this.direction() === 'asc' ? 'desc' : '';
    this.sortChange.emit({ active: this.uiSortHeader(), direction: next });
  }
}
