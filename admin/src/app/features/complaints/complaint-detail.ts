import { DatePipe } from '@angular/common';
import { Component, computed, effect, inject, input, signal } from '@angular/core';
import { FormControl, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatDialog } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSnackBar } from '@angular/material/snack-bar';
import { RouterLink } from '@angular/router';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { Observable, filter, switchMap } from 'rxjs';
import { ComplaintDetail } from '../../core/models';
import { ReasonDialogComponent } from '../professionals/reason-dialog';
import { ComplaintsService } from './complaints.service';

/** Traitement d'une réclamation : prise en charge, échanges avec le déposant, résolution ou rejet. */
@Component({
  selector: 'app-complaint-detail',
  imports: [
    ReactiveFormsModule, DatePipe, RouterLink, MatCardModule, MatButtonModule, MatFormFieldModule, MatInputModule,
    MatProgressBarModule, TranslocoPipe,
  ],
  templateUrl: './complaint-detail.html',
  styleUrl: './complaint-detail.scss',
})
export class ComplaintDetailComponent {
  /** Paramètre de route :id (withComponentInputBinding). */
  readonly id = input.required<string>();

  private readonly api = inject(ComplaintsService);
  private readonly dialog = inject(MatDialog);
  private readonly snack = inject(MatSnackBar);
  private readonly i18n = inject(TranslocoService);

  protected readonly detail = signal<ComplaintDetail | null>(null);
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
      .open<ReasonDialogComponent, { titleKey: string; labelKey: string }, string>(ReasonDialogComponent, {
        data: { titleKey, labelKey },
      })
      .afterClosed()
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
      this.snack.open(this.i18n.translate('complaints.saved'), undefined, { duration: 3000 });
    };
  }
}
