import { Component, input } from '@angular/core';
import { UiSkeleton } from './skeleton';

/** Squelette d'un tableau en cours de premier chargement. */
@Component({
  selector: 'ui-table-skeleton',
  imports: [UiSkeleton],
  template: `
    <div class="ui-table-card" role="status" aria-busy="true">
      <div class="flex flex-col gap-3 p-4">
        @for (i of lines(); track i) { <ui-skeleton class="h-6" /> }
      </div>
    </div>
  `,
})
export class UiTableSkeleton {
  readonly count = input(6);
  protected lines(): number[] {
    return Array.from({ length: this.count() }, (_, i) => i);
  }
}
