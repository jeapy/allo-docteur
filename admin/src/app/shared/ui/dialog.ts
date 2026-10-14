import { Injectable, ViewContainerRef, inject } from '@angular/core';
import { BrnDialogRef, BrnDialogService, injectBrnDialogContext } from '@spartan-ng/brain/dialog';
import { ComponentType } from '@angular/cdk/overlay';
import { Observable, map } from 'rxjs';

const PANEL =
  'w-[min(32rem,calc(100vw-2rem))] max-h-[90vh]! overflow-y-auto rounded-lg border border-border bg-surface p-6 shadow-lg ' +
  'data-[state=open]:animate-in data-[state=open]:fade-in-0 data-[state=open]:zoom-in-95 duration-200';
const BACKDROP = 'bg-slate-900/40 backdrop-blur-[2px] data-[state=open]:animate-in data-[state=open]:fade-in-0 duration-200';

/**
 * Modales construites sur le dialogue headless de Spartan (piège du focus, Échap, ARIA, restauration du focus).
 * `open` rend le résultat de la modale (ou `undefined` si fermée sans valider).
 */
@Injectable({ providedIn: 'root' })
export class UiDialog {
  private readonly service = inject(BrnDialogService);

  open<R = unknown, D = unknown>(
    component: ComponentType<unknown>,
    data?: D,
    vcr?: ViewContainerRef,
  ): Observable<R | undefined> {
    const ref: BrnDialogRef<R> = this.service.open<D, R>(component, vcr, data, {
      panelClass: PANEL.split(' '),
      backdropClass: BACKDROP.split(' '),
      role: 'dialog',
      ariaModal: true,
      autoFocus: 'first-tabbable',
    });
    return ref.closed$.pipe(map((r) => r));
  }
}

/**
 * Entité passée à un formulaire de création/modification. Lorsqu'on ouvre la modale sans donnée (création),
 * Spartan fournit un objet de contexte vide plutôt que `null` : on ne le prend pour une entité que s'il a un `id`.
 */
export function injectDialogEntity<T extends { id: unknown }>(): T | null {
  const ctx = injectBrnDialogContext<T | null>();
  return ctx && typeof ctx === 'object' && 'id' in ctx ? ctx : null;
}
