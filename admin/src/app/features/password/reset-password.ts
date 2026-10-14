import { Component, inject, signal } from '@angular/core';
import { AbstractControl, FormBuilder, ReactiveFormsModule, ValidationErrors, Validators } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { NgIcon } from '@ng-icons/core';
import { TranslocoPipe } from '@jsverse/transloco';
import { AuthService } from '../../core/auth.service';
import { UiButton, UiField, UiInput } from '../../shared/ui';

/** Longueur minimale imposée par le serveur. */
export const MIN_PASSWORD_LENGTH = 10;

export function passwordsMatch(group: AbstractControl): ValidationErrors | null {
  const { password, confirm } = group.value as { password: string; confirm: string };
  return password === confirm ? null : { mismatch: true };
}

/** Choix d'un nouveau mot de passe depuis le lien reçu par e-mail (`?token=`). */
@Component({
  selector: 'app-reset-password',
  imports: [ReactiveFormsModule, RouterLink, NgIcon, UiButton, UiField, UiInput, TranslocoPipe],
  styleUrl: '../login/login.scss',
  template: `
    <div class="login">
      <aside class="login__brand" aria-hidden="true">
        <div class="login__brand-inner">
          <span class="login__logo"><ng-icon name="lucideHeartPulse" size="28" /></span>
          <p class="login__tagline">{{ 'login.tagline' | transloco }}</p>
        </div>
      </aside>
      <main class="login__panel">
        <section class="login__card" [attr.aria-busy]="loading()">
          <h1 class="login__title">{{ 'app.name' | transloco }}</h1>
          <p class="login__subtitle">{{ 'password.resetTitle' | transloco }}</p>
          @if (done()) {
            <p class="login__hint" role="status">{{ 'password.done' | transloco }}</p>
          } @else if (!token) {
            <p class="login__error" role="alert">{{ 'password.missingToken' | transloco }}</p>
          } @else {
            <form [formGroup]="form" (ngSubmit)="submit()" novalidate class="login__form">
              <ui-field [label]="'password.new' | transloco" inputId="rp-password"
                [error]="form.controls.password.touched && form.controls.password.invalid ? ('password.tooShort' | transloco: { min: min }) : null">
                <input uiInput id="rp-password" type="password" formControlName="password" autocomplete="new-password" />
              </ui-field>
              <ui-field [label]="'password.confirm' | transloco" inputId="rp-confirm"
                [error]="form.controls.confirm.touched && form.hasError('mismatch') ? ('password.mismatch' | transloco) : null">
                <input uiInput id="rp-confirm" type="password" formControlName="confirm" autocomplete="new-password" />
              </ui-field>
              <button uiBtn size="lg" type="submit" [disabled]="form.invalid || loading()">{{ 'password.submit' | transloco }}</button>
            </form>
          }
          <p class="login__hint" style="margin-top: 1.5rem">
            <a routerLink="/login" class="text-primary-hover underline">{{ 'password.backToLogin' | transloco }}</a>
          </p>
        </section>
      </main>
    </div>
  `,
})
export class ResetPasswordComponent {
  private readonly auth = inject(AuthService);
  private readonly fb = inject(FormBuilder).nonNullable;
  protected readonly token = inject(ActivatedRoute).snapshot.queryParamMap.get('token');

  protected readonly min = MIN_PASSWORD_LENGTH;
  protected readonly loading = signal(false);
  protected readonly done = signal(false);
  protected readonly form = this.fb.group(
    {
      password: ['', [Validators.required, Validators.minLength(MIN_PASSWORD_LENGTH), Validators.maxLength(128)]],
      confirm: ['', [Validators.required]],
    },
    { validators: passwordsMatch },
  );

  protected submit(): void {
    if (this.form.invalid || this.loading() || !this.token) return;
    this.loading.set(true);
    this.auth.resetPassword(this.token, this.form.getRawValue().password).subscribe({
      next: () => this.done.set(true),
      error: () => this.loading.set(false), // lien invalide ou expiré : message du serveur affiché par l'intercepteur
    });
  }
}
