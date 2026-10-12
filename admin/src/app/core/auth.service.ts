import { HttpClient } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { Observable, finalize, map, of, shareReplay, tap, catchError, switchMap } from 'rxjs';
import { API_BASE_URL } from './config';
import { CurrentUser, LoginResponse, Role, TokenResponse } from './models';

/**
 * Access token gardé en mémoire uniquement. Le refresh token est un cookie httpOnly
 * (envoyé avec withCredentials) : il n'est jamais lisible par le JavaScript.
 */
@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly router = inject(Router);
  private readonly base = inject(API_BASE_URL);

  private readonly _token = signal<string | null>(null);
  private readonly _user = signal<CurrentUser | null>(null);
  private refreshInFlight$: Observable<string> | null = null;

  readonly user = this._user.asReadonly();
  readonly isAuthenticated = computed(() => this._token() !== null && this._user() !== null);

  get accessToken(): string | null {
    return this._token();
  }

  hasRole(...roles: Role[]): boolean {
    const u = this._user();
    return !!u && roles.includes(u.role);
  }

  login(email: string, password: string): Observable<LoginResponse> {
    return this.http
      .post<LoginResponse>(`${this.base}/auth/login`, { email, password }, { withCredentials: true })
      .pipe(tap((r) => this.applyIfAuthenticated(r)));
  }

  verifyMfa(mfaToken: string, code: string): Observable<LoginResponse> {
    return this.http
      .post<LoginResponse>(`${this.base}/auth/2fa/verify`, { mfaToken, code }, { withCredentials: true })
      .pipe(tap((r) => this.applyIfAuthenticated(r)));
  }

  /** Une seule requête de refresh à la fois ; les appels concurrents partagent le résultat. */
  refresh(): Observable<string> {
    if (!this.refreshInFlight$) {
      this.refreshInFlight$ = this.http
        .post<TokenResponse>(`${this.base}/auth/refresh`, {}, { withCredentials: true })
        .pipe(
          map((r) => r.accessToken),
          tap((t) => this._token.set(t)),
          finalize(() => (this.refreshInFlight$ = null)),
          shareReplay({ bufferSize: 1, refCount: false }),
        );
    }
    return this.refreshInFlight$;
  }

  /** Tente de restaurer la session au démarrage grâce au cookie de refresh. */
  restoreSession(): Observable<boolean> {
    return this.refresh().pipe(
      switchMap(() => this.http.get<CurrentUser>(`${this.base}/auth/me`)),
      tap((u) => this._user.set(u)),
      map(() => true),
      catchError(() => {
        this.clear();
        return of(false);
      }),
    );
  }

  logout(redirect = true): void {
    this.http.post(`${this.base}/auth/logout`, {}, { withCredentials: true }).subscribe({ error: () => undefined });
    this.clear();
    if (redirect) void this.router.navigate(['/login']);
  }

  /** Utilisé quand le refresh échoue : on nettoie sans rappeler le serveur. */
  expireSession(): void {
    this.clear();
    void this.router.navigate(['/login']);
  }

  private applyIfAuthenticated(r: LoginResponse): void {
    if (r.status === 'AUTHENTICATED') {
      this._token.set(r.accessToken);
      this._user.set(r.user);
    }
  }

  private clear(): void {
    this._token.set(null);
    this._user.set(null);
  }
}
