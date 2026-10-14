import { DatePipe, DecimalPipe } from '@angular/common';
import { Component, effect, inject, input, signal } from '@angular/core';
import { NgIcon } from '@ng-icons/core';
import { RouterLink } from '@angular/router';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { ToastService, UiBadge, UiButton, UiCard, UiDialog, UiEmptyState, UiPageHeader, UiSkeleton, statusTone } from '../../shared/ui';
import { Observable, filter, switchMap } from 'rxjs';
import { AuthService } from '../../core/auth.service';
import { ProfessionalDetail, ProfessionalDocument } from '../../core/models';
import { ProfessionalsService } from './professionals.service';
import { ReasonDialogComponent } from './reason-dialog';

@Component({
  selector: 'app-professional-detail',
  imports: [
    DatePipe, DecimalPipe, RouterLink, TranslocoPipe, NgIcon, UiPageHeader, UiBadge, UiButton, UiEmptyState, UiCard, UiSkeleton,
  ],
  templateUrl: './professional-detail.html',
})
export class ProfessionalDetailComponent {
  /** Paramètre de route :id (withComponentInputBinding). */
  readonly id = input.required<string>();

  private readonly api = inject(ProfessionalsService);
  private readonly dialog = inject(UiDialog);
  private readonly toast = inject(ToastService);
  private readonly i18n = inject(TranslocoService);
  private readonly auth = inject(AuthService);

  protected readonly pro = signal<ProfessionalDetail | null>(null);
  protected readonly tone = statusTone;
  protected readonly busy = signal(false);

  /** Seuls les niveaux SUPER et SUPPORT peuvent décider ; FINANCE consulte en lecture seule. */
  protected readonly canReview = () => ['SUPER', 'SUPPORT'].includes(this.auth.user()?.adminLevel ?? '');

  constructor() {
    effect(() => {
      this.busy.set(true);
      this.api.get(this.id()).subscribe({
        next: (p) => {
          this.pro.set(p);
          this.busy.set(false);
        },
        error: () => this.busy.set(false),
      });
    });
  }

  protected approve(): void {
    this.run(this.api.approve(this.id()));
  }

  protected reject(): void {
    this.askReason('professionals.rejectTitle').pipe(switchMap((r) => this.api.reject(this.id(), r))).subscribe(this.handle());
  }

  protected suspend(): void {
    this.askReason('professionals.suspendTitle').pipe(switchMap((r) => this.api.suspend(this.id(), r))).subscribe(this.handle());
  }

  protected open(doc: ProfessionalDocument): void {
    this.api.document(doc.id).subscribe((blob) => {
      const url = URL.createObjectURL(blob);
      window.open(url, '_blank', 'noopener');
      setTimeout(() => URL.revokeObjectURL(url), 60_000);
    });
  }

  private askReason(titleKey: string): Observable<string> {
    return this.dialog
      .open<string, { titleKey: string }>(ReasonDialogComponent, { titleKey })
      .pipe(filter((r): r is string => !!r));
  }

  private run(call: Observable<ProfessionalDetail>): void {
    this.busy.set(true);
    call.subscribe(this.handle());
  }

  private handle() {
    return {
      next: (p: ProfessionalDetail) => {
        this.pro.set(p);
        this.busy.set(false);
        this.toast.success(this.i18n.translate('professionals.updated'));
      },
      error: () => this.busy.set(false),
    };
  }
}
