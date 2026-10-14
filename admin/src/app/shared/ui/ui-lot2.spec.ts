import { Component, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideTransloco } from '@jsverse/transloco';
import { provideIcons } from '@ng-icons/core';
import { of } from 'rxjs';
import { describe, expect, it } from 'vitest';
import { UI_ICONS } from './icons';
import { SortChange, UiSortHeader } from './sort-header';
import { statusTone } from './status-tone';
import { UiTabs, UiTab } from './tabs';

class EmptyLoader {
  getTranslation() {
    return of({});
  }
}
const providers = [
  provideTransloco({ config: { availableLangs: ['fr'], defaultLang: 'fr', prodMode: true }, loader: EmptyLoader }),
  provideIcons(UI_ICONS),
];

@Component({
  imports: [UiSortHeader],
  template: `<table><thead><tr>
    <th uiSortHeader="name" [sort]="sort()" (sortChange)="events.push($event)">Nom</th>
  </tr></thead></table>`,
})
class SortHost {
  readonly sort = signal<string | undefined>(undefined);
  readonly events: SortChange[] = [];
}

describe('UiSortHeader', () => {
  it('cycle ascendant → descendant → aucun et expose aria-sort', () => {
    TestBed.configureTestingModule({ providers });
    const f = TestBed.createComponent(SortHost);
    f.detectChanges();
    const th: HTMLElement = f.nativeElement.querySelector('th');
    const click = () => th.querySelector('button')!.click();

    expect(th.getAttribute('aria-sort')).toBe('none');
    click();
    f.componentInstance.sort.set('name,asc');
    f.detectChanges();
    expect(th.getAttribute('aria-sort')).toBe('ascending');
    click();
    f.componentInstance.sort.set('name,desc');
    f.detectChanges();
    expect(th.getAttribute('aria-sort')).toBe('descending');
    click();

    expect(f.componentInstance.events).toEqual([
      { active: 'name', direction: 'asc' },
      { active: 'name', direction: 'desc' },
      { active: 'name', direction: '' },
    ]);
  });

  it('ignore un tri portant sur une autre colonne', () => {
    TestBed.configureTestingModule({ providers });
    const f = TestBed.createComponent(SortHost);
    f.componentInstance.sort.set('email,asc');
    f.detectChanges();
    expect(f.nativeElement.querySelector('th').getAttribute('aria-sort')).toBe('none');
  });
});

@Component({
  imports: [UiTabs],
  template: `<ui-tabs [tabs]="tabs" [(active)]="active" />`,
})
class TabsHost {
  readonly tabs: UiTab[] = [
    { id: 'a', labelKey: 'a' },
    { id: 'b', labelKey: 'b' },
  ];
  readonly active = signal('a');
}

describe('UiTabs', () => {
  it('change d’onglet au clic et aux flèches du clavier', () => {
    TestBed.configureTestingModule({ providers });
    const f = TestBed.createComponent(TabsHost);
    f.detectChanges();
    const tabs = f.nativeElement.querySelectorAll('[role=tab]') as NodeListOf<HTMLElement>;
    expect(tabs[0].getAttribute('aria-selected')).toBe('true');

    tabs[1].click();
    f.detectChanges();
    expect(f.componentInstance.active()).toBe('b');

    f.nativeElement.querySelector('[role=tablist]').dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowRight', bubbles: true }));
    f.detectChanges();
    expect(f.componentInstance.active()).toBe('a'); // boucle
  });
});

describe('statusTone', () => {
  it('associe les statuts métier à une couleur, neutre par défaut', () => {
    expect(statusTone('ACTIVE')).toBe('success');
    expect(statusTone('PENDING')).toBe('warning');
    expect(statusTone('REJECTED')).toBe('danger');
    expect(statusTone('CONFIRMED')).toBe('info');
    expect(statusTone('???')).toBe('neutral');
    expect(statusTone(null)).toBe('neutral');
  });
});
