import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { ReactiveFormsModule, FormControl, Validators } from '@angular/forms';
import { provideTransloco } from '@jsverse/transloco';
import { of } from 'rxjs';
import { describe, expect, it } from 'vitest';
import { UiBadge } from './badge';
import { UiButton } from './button';
import { cn } from './cn';
import { UiField, UiInput } from './field';
import { PageChange, UiPagination } from './pagination';

class EmptyLoader {
  getTranslation() {
    return of({});
  }
}

const transloco = [
  provideTransloco({
    config: { availableLangs: ['fr'], defaultLang: 'fr', prodMode: true },
    loader: EmptyLoader,
  }),
];

describe('cn', () => {
  it('fusionne et résout les conflits Tailwind (la dernière classe gagne)', () => {
    expect(cn('px-2 py-1', 'px-4')).toBe('py-1 px-4');
    expect(cn('a', false && 'b', undefined, 'c')).toBe('a c');
  });
});

@Component({
  imports: [UiButton, UiBadge],
  template: `<button uiBtn variant="danger" size="sm" id="b">x</button><ui-badge tone="success" id="g">ok</ui-badge>`,
})
class Host {}

describe('UiButton / UiBadge', () => {
  it('applique les variantes et tailles', () => {
    const f = TestBed.createComponent(Host);
    f.detectChanges();
    const b: HTMLElement = f.nativeElement.querySelector('#b');
    expect(b.className).toContain('bg-danger');
    expect(b.className).toContain('h-8');
    expect(f.nativeElement.querySelector('#g').className).toContain('text-success');
  });
});

@Component({
  imports: [ReactiveFormsModule, UiField, UiInput],
  template: `<ui-field label="E-mail" inputId="e" [error]="err"><input uiInput id="e" [formControl]="c" /></ui-field>`,
})
class FieldHost {
  c = new FormControl('', Validators.required);
  err: string | null = null;
}

describe('UiField', () => {
  it("lie le libellé au champ et n'affiche l'erreur qu'une fois le champ touché", () => {
    const f = TestBed.createComponent(FieldHost);
    f.detectChanges();
    const input: HTMLInputElement = f.nativeElement.querySelector('input');
    expect(f.nativeElement.querySelector('label').getAttribute('for')).toBe('e');
    expect(input.getAttribute('aria-invalid')).toBe('false');
    f.componentInstance.c.markAsTouched();
    f.componentInstance.err = 'Champ obligatoire';
    f.detectChanges();
    expect(input.getAttribute('aria-invalid')).toBe('true');
    expect(f.nativeElement.querySelector('[role=alert]').textContent).toContain('Champ obligatoire');
  });
});

describe('UiPagination', () => {
  it('émet la page suivante et revient à 0 quand la taille change', () => {
    TestBed.configureTestingModule({ providers: transloco });
    const f = TestBed.createComponent(UiPagination);
    f.componentRef.setInput('length', 95);
    f.componentRef.setInput('pageIndex', 1);
    f.componentRef.setInput('pageSize', 20);
    f.detectChanges();
    const events: PageChange[] = [];
    f.componentInstance.page.subscribe((e) => events.push(e));
    const buttons = f.nativeElement.querySelectorAll('button');
    buttons[1].click(); // suivant
    expect(events[0]).toEqual({ pageIndex: 2, pageSize: 20 });
    const select: HTMLSelectElement = f.nativeElement.querySelector('select');
    select.value = '50';
    select.dispatchEvent(new Event('change'));
    expect(events[1]).toEqual({ pageIndex: 0, pageSize: 50 });
  });

  it('désactive « précédent » sur la première page', () => {
    TestBed.configureTestingModule({ providers: transloco });
    const f = TestBed.createComponent(UiPagination);
    f.componentRef.setInput('length', 10);
    f.componentRef.setInput('pageIndex', 0);
    f.componentRef.setInput('pageSize', 20);
    f.detectChanges();
    const buttons = f.nativeElement.querySelectorAll('button');
    expect(buttons[0].disabled).toBe(true);
    expect(buttons[1].disabled).toBe(true); // une seule page
  });
});
