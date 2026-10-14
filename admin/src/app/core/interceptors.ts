import { HttpErrorResponse, HttpHandlerFn, HttpInterceptorFn, HttpRequest } from '@angular/common/http';
import { inject } from '@angular/core';
import { TranslocoService } from '@jsverse/transloco';
import { catchError, switchMap, throwError } from 'rxjs';
import { ToastService } from '../shared/ui/toast';
import { AuthService } from './auth.service';
import { API_BASE_URL } from './config';
import { ProblemDetail } from './models';

const AUTH_PATHS = ['/auth/login', '/auth/2fa/verify', '/auth/refresh', '/auth/logout'];

function isAuthCall(req: HttpRequest<unknown>, base: string): boolean {
  return AUTH_PATHS.some((p) => req.url === base + p);
}

function withToken(req: HttpRequest<unknown>, token: string | null): HttpRequest<unknown> {
  return token ? req.clone({ setHeaders: { Authorization: `Bearer ${token}` } }) : req;
}

/** Ajoute le JWT ; sur 401, rafraîchit une fois puis rejoue la requête. */
export const authInterceptor: HttpInterceptorFn = (req, next: HttpHandlerFn) => {
  const auth = inject(AuthService);
  const base = inject(API_BASE_URL);
  if (!req.url.startsWith(base) || isAuthCall(req, base)) return next(req);

  return next(withToken(req, auth.accessToken)).pipe(
    catchError((err: unknown) => {
      if (err instanceof HttpErrorResponse && err.status === 401) {
        return auth.refresh().pipe(
          switchMap((token) => next(withToken(req, token))),
          catchError((refreshErr: unknown) => {
            auth.expireSession();
            return throwError(() => refreshErr);
          }),
        );
      }
      return throwError(() => err);
    }),
  );
};

/** Affiche une notification pour les erreurs API (ProblemDetail), sauf 401 gérés ailleurs. */
export const errorInterceptor: HttpInterceptorFn = (req, next) => {
  const toast = inject(ToastService);
  const i18n = inject(TranslocoService);
  const base = inject(API_BASE_URL);
  return next(req).pipe(
    catchError((err: unknown) => {
      if (err instanceof HttpErrorResponse && req.url.startsWith(base) && err.status !== 401) {
        toast.error(messageFor(err, i18n));
      }
      return throwError(() => err);
    }),
  );
};

export function messageFor(err: HttpErrorResponse, i18n: TranslocoService): string {
  if (err.status === 0) return i18n.translate('errors.network');
  if (err.status === 403) return i18n.translate('errors.forbidden');
  if (err.status === 429) return i18n.translate('errors.tooMany');
  const problem = err.error as ProblemDetail | null;
  return problem?.detail || i18n.translate('errors.generic');
}
