import { Component, input } from '@angular/core';

/** En-tête de page : titre, sous-titre et zone d'actions (contenu projeté). */
@Component({
  selector: 'ui-page-header',
  template: `
    <div class="mb-6 flex flex-wrap items-end justify-between gap-4">
      <div class="min-w-0">
        <h1 class="m-0 text-2xl font-semibold tracking-tight text-text">{{ title() }}</h1>
        @if (subtitle()) { <p class="m-0 mt-1 text-sm text-muted">{{ subtitle() }}</p> }
      </div>
      <div class="flex items-center gap-2 empty:hidden"><ng-content /></div>
    </div>
  `,
})
export class UiPageHeader {
  readonly title = input.required<string>();
  readonly subtitle = input<string>();
}
