import { Component, ElementRef, inject, input, model } from '@angular/core';
import { TranslocoPipe } from '@jsverse/transloco';

export interface UiTab {
  id: string;
  /** Clé de traduction du libellé. */
  labelKey: string;
}

/**
 * Onglets accessibles (rôles tablist/tab, flèches gauche/droite). Le contenu est géré par le parent :
 * `<ui-tabs [tabs]="tabs" [(active)]="tab" /> @if (tab() === 'x') { … }`.
 */
@Component({
  selector: 'ui-tabs',
  imports: [TranslocoPipe],
  template: `
    <div role="tablist" class="flex gap-1 overflow-x-auto border-b border-border" (keydown)="onKey($event)">
      @for (t of tabs(); track t.id) {
        <button type="button" role="tab" [id]="'tab-' + t.id" [attr.aria-selected]="t.id === active()"
          [attr.tabindex]="t.id === active() ? 0 : -1"
          class="-mb-px cursor-pointer whitespace-nowrap border-0 border-b-2 bg-transparent px-4 py-2.5 text-sm font-medium
                 transition-colors duration-150 ease-ds hover:text-text"
          [class.border-primary]="t.id === active()" [class.text-primary]="t.id === active()"
          [class.border-transparent]="t.id !== active()" [class.text-muted]="t.id !== active()"
          (click)="active.set(t.id)">{{ t.labelKey | transloco }}</button>
      }
    </div>
  `,
  host: { class: 'block' },
})
export class UiTabs {
  readonly tabs = input.required<UiTab[]>();
  readonly active = model.required<string>();
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);

  protected onKey(e: KeyboardEvent): void {
    const dir = e.key === 'ArrowRight' ? 1 : e.key === 'ArrowLeft' ? -1 : 0;
    if (!dir) return;
    const tabs = this.tabs();
    const i = tabs.findIndex((t) => t.id === this.active());
    const next = tabs[(i + dir + tabs.length) % tabs.length];
    this.active.set(next.id);
    queueMicrotask(() => this.host.nativeElement.querySelector<HTMLElement>(`#tab-${next.id}`)?.focus());
  }
}
