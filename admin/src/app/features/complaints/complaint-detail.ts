import { DatePipe } from '@angular/common';
import { Component, computed, effect, inject, input, signal } from '@angular/core';
import { FormControl, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { NgIcon } from '@ng-icons/core';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { ToastService, UiBadge, UiButton, UiCard, UiDialog, UiEmptyState, UiField, UiInput, UiPageHeader, UiSkeleton, statusTone } from '../../shared/ui';
import { Observable, filter, switchMap } from 'rxjs';
import { ComplaintDetail } from '../../core/models';
import { ReasonDialogComponent } from '../professionals/reason-dialog';
import { ComplaintsService } from './complaints.service';

/** Traitement d'une réclamation : prise en charge, échanges avec le déposant, résolution ou rejet. */
@Component({
  selector: 'app-complaint-detail',
  imports: [
    ReactiveFormsModule, DatePipe, RouterLink, TranslocoPipe, NgIcon, UiPageHeader, UiField, UiInput, UiBadge, UiButton, UiEmptyState, UiCard, UiSkeleton,
  ],
  templateUrl: './complaint-detail.html',
})
export class ComplaintDetailComponent {
  /** Paramètre de route :id (withComponentInputBinding). */
  readonly id = input.required<string>();

  private readonly api = inject(ComplaintsService);
  private readonly dialog = inject(UiDialog);
  private readonly toast = inject(ToastService);
  private readonly i18n = inject(TranslocoService);

  protected readonly detail = signal<ComplaintDetail | null>(null);
  protected readonly tone = statusTone;
  protected readonly busy = signal(false);
  protected readonly closed = computed(() => {
    const s = this.detail()?.summary.status;
    return s === 'RESOLVED' || s === 'REJECTED';
  });
  protected readonly message = new FormControl('', {
    nonNullable: true,
    validators: [Validators.required, Validators.maxLength(5000), Validators.pattern(/\S/)],
  });

  constructor() {
    effect(() => {
      this.busy.set(true);
      this.api.get(this.id()).subscribe({
        next: (d) => {
          this.detail.set(d);
          this.busy.set(false);
        },
        error: () => this.busy.set(false),
      });
    });
  }

  protected take(): void {
    this.run(this.api.take(this.id()));
  }

  protected send(): void {
    if (this.message.invalid) {
      this.message.markAsTouched();
      return;
    }
    const body = this.message.value.trim();
    this.run(this.api.reply(this.id(), body), () => this.message.reset(''));
  }

  protected resolve(): void {
    this.askText('complaints.resolveTitle', 'complaints.resolution')
      .pipe(switchMap((t) => this.api.resolve(this.id(), t)))
      .subscribe(this.handle());
  }

  protected reject(): void {
    this.askText('complaints.rejectTitle', 'complaints.rejectReason')
      .pipe(switchMap((t) => this.api.reject(this.id(), t)))
      .subscribe(this.handle());
  }

  private askText(titleKey: string, labelKey: string): Observable<string> {
    return this.dialog
      .open<string, { titleKey: string; labelKey: string }>(ReasonDialogComponent, { titleKey, labelKey })
      .pipe(filter((t): t is string => !!t));
  }

  private run(call: Observable<ComplaintDetail>, after?: () => void): void {
    this.busy.set(true);
    call.subscribe({ next: (d) => { this.handle(after)(d); }, error: () => this.busy.set(false) });
  }

  private handle(after?: () => void): (d: ComplaintDetail) => void {
    return (d) => {
      this.detail.set(d);
      this.busy.set(false);
      after?.();
      this.toast.success(this.i18n.translate('complaints.saved'));
    };
  }
}
