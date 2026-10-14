import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { FormControl, FormGroup } from '@angular/forms';
import { beforeEach, describe, expect, it } from 'vitest';
import { AuthService } from '../../core/auth.service';
import { passwordsMatch } from './reset-password';

describe('mot de passe oublié', () => {
  let auth: AuthService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    auth = TestBed.inject(AuthService);
    http = TestBed.inject(HttpTestingController);
  });

  it("demande le lien sans révéler l'existence du compte", () => {
    auth.forgotPassword('a@b.ci').subscribe();
    const req = http.expectOne('/api/v1/auth/password/forgot');
    expect(req.request.body).toEqual({ email: 'a@b.ci' });
  });

  it('envoie le jeton et le nouveau mot de passe', () => {
    auth.resetPassword('tok', 'NouveauMotDePasse1!').subscribe();
    const req = http.expectOne('/api/v1/auth/password/reset');
    expect(req.request.body).toEqual({ token: 'tok', newPassword: 'NouveauMotDePasse1!' });
  });

  it('refuse deux mots de passe différents', () => {
    const g = (a: string, b: string) => new FormGroup({ password: new FormControl(a), confirm: new FormControl(b) });
    expect(passwordsMatch(g('abcdefghij', 'abcdefghij'))).toBeNull();
    expect(passwordsMatch(g('abcdefghij', 'abcdefghik'))).toEqual({ mismatch: true });
  });
});
