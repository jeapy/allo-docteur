import { Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { TranslocoPipe } from '@jsverse/transloco';
import { NgIcon } from '@ng-icons/core';
import QRCode from 'qrcode';
import { AuthService } from '../../core/auth.service';
import { TotpSetup } from '../../core/models';
import { UiButton, UiCard, UiField, UiInput, UiSkeleton } from '../../shared/ui';

type Step = 'scan' | 'recovery';

/**
 * Enrôlement TOTP obligatoire (administrateurs SUPER et FINANCE) :
 * 1. scanner le QR code (ou saisir la clé) et confirmer avec un code ;
 * 2. conserver les codes de secours, affichés une seule fois.
 */
@Component({
  selector: 'app-two-factor-setup',
  imports: [ReactiveFormsModule, NgIcon, TranslocoPipe, UiButton, UiCard, UiField, UiInput, UiSkeleton],
  templateUrl: './two-factor-setup.html',
})
export class TwoFactorSetupComponent {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly fb = inject(FormBuilder).nonNullable;
  private readonly destroyRef = inject(DestroyRef);

  protected readonly step = signal<Step>('scan');
  protected readonly setup = signal<TotpSetup | null>(null);
  protected readonly qr = signal<string | null>(null);
  protected readonly recoveryCodes = signal<string[]>([]);
  protected readonly loading = signal(false);
  protected readonly failed = signal(false);

  protected readonly form = this.fb.group({
    code: ['', [Validators.required, Validators.pattern(/^\d{6}$/)]],
  });

  constructor() {
    this.auth
      .setupTwoFactor()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((s) => {
        this.setup.set(s);
        void QRCode.toDataURL(s.otpauthUri, { margin: 1, width: 192 }).then((url) => this.qr.set(url));
      });
  }

  protected confirm(): void {
    if (this.form.invalid || this.loading()) return;
    this.loading.set(true);
    this.failed.set(false);
    this.auth.enableTwoFactor(this.form.getRawValue().code).subscribe({
      next: (r) => {
        this.loading.set(false);
        this.recoveryCodes.set(r.recoveryCodes);
        this.step.set('recovery');
      },
      error: () => {
        this.loading.set(false);
        this.failed.set(true);
      },
    });
  }

  protected finish(): void {
    this.loading.set(true);
    this.auth.completeTwoFactorEnrollment().subscribe({
      next: () => void this.router.navigate(['/']),
      error: () => this.loading.set(false),
    });
  }

  protected logout(): void {
    this.auth.logout();
  }
}
