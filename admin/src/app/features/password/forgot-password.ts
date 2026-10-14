import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { NgIcon } from '@ng-icons/core';
import { TranslocoPipe } from '@jsverse/transloco';
import { AuthService } from '../../core/auth.service';
import { UiButton, UiField, UiInput } from '../../shared/ui';

/** Demande d'un lien de réinitialisation. La confirmation est la même que le compte existe ou non. */
@Component({
  selector: 'app-forgot-password',
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
          <p class="login__subtitle">{{ 'password.forgotTitle' | transloco }}</p>
          @if (sent()) {
            <p class="login__hint" role="status">{{ 'password.sent' | transloco }}</p>
          } @else {
            <form [formGroup]="form" (ngSubmit)="submit()" novalidate class="login__form">
              <p class="login__hint">{{ 'password.forgotHint' | transloco }}</p>
              <ui-field [label]="'login.email' | transloco" inputId="fp-email"
                [error]="form.controls.email.touched && form.controls.email.invalid ? ('validation.email' | transloco) : null">
                <input uiInput id="fp-email" type="email" formControlName="email" autocomplete="username" />
              </ui-field>
              <button uiBtn size="lg" type="submit" [disabled]="form.invalid || loading()">{{ 'password.send' | transloco }}</button>
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
export class ForgotPasswordComponent {
  private readonly auth = inject(AuthService);
  private readonly fb = inject(FormBuilder).nonNullable;

  protected readonly loading = signal(false);
  protected readonly sent = signal(false);
  protected readonly form = this.fb.group({ email: ['', [Validators.required, Validators.email]] });

  protected submit(): void {
    if (this.form.invalid || this.loading()) return;
    this.loading.set(true);
    this.auth.forgotPassword(this.form.getRawValue().email.trim()).subscribe({
      next: () => this.sent.set(true),
      error: () => this.loading.set(false), // l'intercepteur affiche l'erreur (réseau, trop de tentatives)
    });
  }
}
