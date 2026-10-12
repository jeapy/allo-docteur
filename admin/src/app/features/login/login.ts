import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { Router } from '@angular/router';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { AuthService } from '../../core/auth.service';
import { LoginResponse } from '../../core/models';

@Component({
  selector: 'app-login',
  imports: [
    ReactiveFormsModule, MatCardModule, MatFormFieldModule, MatInputModule,
    MatButtonModule, MatProgressBarModule, TranslocoPipe,
  ],
  templateUrl: './login.html',
  styleUrl: './login.scss',
})
export class LoginComponent {
  private readonly fb = inject(FormBuilder).nonNullable;
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly i18n = inject(TranslocoService);

  protected readonly loading = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly mfaToken = signal<string | null>(null);

  protected readonly form = this.fb.group({
    email: ['', [Validators.required, Validators.email]],
    password: ['', [Validators.required]],
  });
  protected readonly mfaForm = this.fb.group({
    code: ['', [Validators.required, Validators.pattern(/^\d{6}$/)]],
  });

  protected submit(): void {
    if (this.form.invalid || this.loading()) return;
    const { email, password } = this.form.getRawValue();
    this.run(this.auth.login(email, password));
  }

  protected verify(): void {
    const token = this.mfaToken();
    if (this.mfaForm.invalid || !token || this.loading()) return;
    this.run(this.auth.verifyMfa(token, this.mfaForm.getRawValue().code));
  }

  protected back(): void {
    this.mfaToken.set(null);
    this.mfaForm.reset();
    this.error.set(null);
  }

  private run(call: ReturnType<AuthService['login']>): void {
    this.loading.set(true);
    this.error.set(null);
    call.subscribe({
      next: (r: LoginResponse) => {
        this.loading.set(false);
        if (r.status === 'MFA_REQUIRED') this.mfaToken.set(r.mfaToken);
        else void this.router.navigate(['/']);
      },
      error: (e: unknown) => {
        this.loading.set(false);
        const status = e instanceof HttpErrorResponse ? e.status : 0;
        this.error.set(this.i18n.translate(status === 401 ? 'errors.invalidCredentials' : 'errors.generic'));
      },
    });
  }
}
