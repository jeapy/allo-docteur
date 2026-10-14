import { DatePipe } from '@angular/common';
import { Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { BrnDialogClose, BrnDialogTitle, injectBrnDialogContext } from '@spartan-ng/brain/dialog';
import { TranslocoPipe } from '@jsverse/transloco';
import { catchError, debounceTime, distinctUntilChanged, EMPTY, of, switchMap } from 'rxjs';
import { PharmacyMember, PharmacyRow, UserSummary } from '../../core/models';
import { ToastService, UiButton, UiField, UiInput } from '../../shared/ui';
import { UsersService } from '../users/users.service';
import { PharmaciesService } from './pharmacies.service';

/**
 * Pharmaciens rattachés à une pharmacie : eux seuls peuvent traiter ses commandes.
 * Le serveur n'accepte que des pharmaciens approuvés et actifs.
 */
@Component({
  selector: 'app-pharmacy-members-dialog',
  imports: [ReactiveFormsModule, DatePipe, TranslocoPipe, BrnDialogTitle, BrnDialogClose, UiField, UiInput, UiButton],
  templateUrl: './pharmacy-members-dialog.html',
})
export class PharmacyMembersDialogComponent {
  protected readonly pharmacy = injectBrnDialogContext<PharmacyRow>();
  private readonly api = inject(PharmaciesService);
  private readonly users = inject(UsersService);
  private readonly toast = inject(ToastService);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly members = signal<PharmacyMember[]>([]);
  protected readonly candidates = signal<UserSummary[]>([]);
  protected readonly search = new FormControl('', { nonNullable: true });

  constructor() {
    this.loadMembers();
    this.search.valueChanges
      .pipe(
        debounceTime(300),
        distinctUntilChanged(),
        switchMap((q) =>
          q.trim().length < 2
            ? of(null)
            : this.users.list({ page: 0, size: 6, q: q.trim(), role: 'PROFESSIONAL' }).pipe(catchError(() => of(null))),
        ),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe((page) => this.candidates.set(page?.content ?? []));
  }

  protected isMember(u: UserSummary): boolean {
    return this.members().some((m) => m.userId === u.id);
  }

  protected add(u: UserSummary): void {
    this.api
      .addMember(this.pharmacy.id, u.id)
      .pipe(catchError(() => EMPTY)) // l'intercepteur affiche déjà « n'est pas un pharmacien approuvé »
      .subscribe(() => {
        this.loadMembers();
        this.search.setValue('');
      });
  }

  protected remove(m: PharmacyMember): void {
    this.api.removeMember(this.pharmacy.id, m.userId).subscribe(() => this.members.update((list) => list.filter((x) => x.userId !== m.userId)));
  }

  private loadMembers(): void {
    this.api
      .members(this.pharmacy.id)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((m) => this.members.set(m));
  }
}
