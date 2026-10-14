import { Injectable, ViewContainerRef, inject } from '@angular/core';
import { BrnDialogRef, BrnDialogService } from '@spartan-ng/brain/dialog';
import { ComponentType } from '@angular/cdk/overlay';
import { Observable, map } from 'rxjs';

const PANEL =
  'w-[calc(100vw-2rem)] max-w-lg rounded-lg border border-border bg-surface p-6 shadow-lg ' +
  'data-[state=open]:animate-in data-[state=open]:fade-in-0 data-[state=open]:zoom-in-95 duration-200';
const BACKDROP = 'bg-slate-900/40 backdrop-blur-[2px] data-[state=open]:animate-in data-[state=open]:fade-in-0 duration-200';

/**
 * Modales construites sur le dialogue headless de Spartan (piège du focus, Échap, ARIA, restauration du focus).
 * `open` rend le résultat de la modale (ou `undefined` si fermée sans valider).
 */
@Injectable({ providedIn: 'root' })
export class UiDialog {
  private readonly service = inject(BrnDialogService);

  open<R = unknown, D extends object = object>(
    component: ComponentType<unknown>,
    data?: D,
    vcr?: ViewContainerRef,
  ): Observable<R | undefined> {
    const ref: BrnDialogRef<R> = this.service.open<D, R>(component, vcr, data, {
      panelClass: PANEL,
      backdropClass: BACKDROP,
      role: 'dialog',
      ariaModal: true,
      autoFocus: 'first-tabbable',
    });
    return ref.closed$.pipe(map((r) => r));
  }
}
