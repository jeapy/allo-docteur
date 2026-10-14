import { Component, input } from '@angular/core';
import { NgIcon } from '@ng-icons/core';

/** État vide : icône, titre, texte et action facultative (contenu projeté). */
@Component({
  selector: 'ui-empty-state',
  imports: [NgIcon],
  template: `
    <div class="flex flex-col items-center gap-2 px-6 py-12 text-center">
      <span class="grid h-12 w-12 place-items-center rounded-full bg-surface-muted text-muted">
        <ng-icon [name]="icon()" size="24" aria-hidden="true" />
      </span>
      <p class="m-0 text-base font-semibold text-text">{{ title() }}</p>
      @if (description()) { <p class="m-0 max-w-sm text-sm text-muted">{{ description() }}</p> }
      <div class="mt-2 empty:hidden"><ng-content /></div>
    </div>
  `,
})
export class UiEmptyState {
  readonly title = input.required<string>();
  readonly description = input<string>();
  readonly icon = input('lucideInbox');
}
