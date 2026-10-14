import { provideHttpClient, withInterceptors, HttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { ToastService } from '../shared/ui/toast';
import { TranslocoService } from '@jsverse/transloco';
import { describe, it, expect, beforeEach, vi } from 'vitest';
import { AuthService } from './auth.service';
import { authInterceptor, errorInterceptor } from './interceptors';
import { CurrentUser } from './models';

const user: CurrentUser = { id: '1', email: 'a@b.c', firstName: 'A', lastName: 'B', role: 'ADMIN' };
const API = '/api/v1';

describe('Auth', () => {
  let http: HttpTestingController;
  let auth: AuthService;
  let client: HttpClient;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideRouter([{ path: 'login', children: [] }]),
        provideHttpClient(withInterceptors([authInterceptor, errorInterceptor])),
        provideHttpClientTesting(),
        { provide: ToastService, useValue: { error: vi.fn(), success: vi.fn(), info: vi.fn() } },
        { provide: TranslocoService, useValue: { translate: (k: string) => k } },
      ],
    });
    http = TestBed.inject(HttpTestingController);
    auth = TestBed.inject(AuthService);
    client = TestBed.inject(HttpClient);
  });

  it('stocke le token en mémoire après un login direct', () => {
    auth.login('a@b.c', 'pw').subscribe();
    http.expectOne(`${API}/auth/login`).flush({ status: 'AUTHENTICATED', accessToken: 'T1', expiresIn: 900, user });
    expect(auth.accessToken).toBe('T1');
    expect(auth.isAuthenticated()).toBe(true);
    expect(auth.hasRole('ADMIN')).toBe(true);
  });

  it("n'authentifie pas tant que la 2FA est requise", () => {
    auth.login('a@b.c', 'pw').subscribe();
    http.expectOne(`${API}/auth/login`).flush({ status: 'MFA_REQUIRED', mfaToken: 'M' });
    expect(auth.isAuthenticated()).toBe(false);
    auth.verifyMfa('M', '123456').subscribe();
    const req = http.expectOne(`${API}/auth/2fa/verify`);
    expect(req.request.body).toEqual({ mfaToken: 'M', code: '123456' });
    req.flush({ status: 'AUTHENTICATED', accessToken: 'T2', expiresIn: 900, user });
    expect(auth.isAuthenticated()).toBe(true);
  });

  it('ajoute le Bearer et rejoue la requête après un seul refresh sur 401', () => {
    auth.login('a@b.c', 'pw').subscribe();
    http.expectOne(`${API}/auth/login`).flush({ status: 'AUTHENTICATED', accessToken: 'OLD', expiresIn: 900, user });

    let a = 0, b = 0;
    client.get(`${API}/admin/users`).subscribe(() => a++);
    client.get(`${API}/admin/stats`).subscribe(() => b++);

    const first = http.expectOne(`${API}/admin/users`);
    const second = http.expectOne(`${API}/admin/stats`);
    expect(first.request.headers.get('Authorization')).toBe('Bearer OLD');
    first.flush({}, { status: 401, statusText: 'Unauthorized' });
    second.flush({}, { status: 401, statusText: 'Unauthorized' });

    // un seul refresh partagé
    http.expectOne(`${API}/auth/refresh`).flush({ accessToken: 'NEW', expiresIn: 900 });
    const r1 = http.expectOne(`${API}/admin/users`);
    const r2 = http.expectOne(`${API}/admin/stats`);
    expect(r1.request.headers.get('Authorization')).toBe('Bearer NEW');
    r1.flush({});
    r2.flush({});
    expect(a).toBe(1);
    expect(b).toBe(1);
  });

  it('déconnecte si le refresh échoue', () => {
    auth.login('a@b.c', 'pw').subscribe();
    http.expectOne(`${API}/auth/login`).flush({ status: 'AUTHENTICATED', accessToken: 'OLD', expiresIn: 900, user });
    client.get(`${API}/admin/users`).subscribe({ error: () => undefined });
    http.expectOne(`${API}/admin/users`).flush({}, { status: 401, statusText: 'Unauthorized' });
    http.expectOne(`${API}/auth/refresh`).flush({}, { status: 401, statusText: 'Unauthorized' });
    expect(auth.isAuthenticated()).toBe(false);
  });

  it('affiche le detail ProblemDetail via un toast', () => {
    const toast = TestBed.inject(ToastService);
    client.get(`${API}/admin/users`).subscribe({ error: () => undefined });
    http.expectOne(`${API}/admin/users`).flush({ detail: 'Boom' }, { status: 400, statusText: 'Bad Request' });
    expect(toast.error).toHaveBeenCalledWith('Boom');
  });
});
