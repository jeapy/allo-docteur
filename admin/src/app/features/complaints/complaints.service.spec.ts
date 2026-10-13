import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { beforeEach, describe, expect, it } from 'vitest';
import { ComplaintsService } from './complaints.service';

describe('ComplaintsService', () => {
  let svc: ComplaintsService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    svc = TestBed.inject(ComplaintsService);
    http = TestBed.inject(HttpTestingController);
  });

  it('filtre par statut côté serveur', () => {
    svc.list({ page: 1, size: 10, status: 'OPEN' }).subscribe();
    const req = http.expectOne((r) => r.url === '/api/v1/admin/complaints');
    expect(req.request.params.get('status')).toBe('OPEN');
    expect(req.request.params.get('page')).toBe('1');
  });

  it("n'envoie pas de statut quand aucun n'est choisi", () => {
    svc.list({ page: 0, size: 20, status: null }).subscribe();
    const req = http.expectOne((r) => r.url === '/api/v1/admin/complaints');
    expect(req.request.params.has('status')).toBe(false);
  });

  it('résout avec le texte de la résolution', () => {
    svc.resolve('c1', 'Remboursé').subscribe();
    const req = http.expectOne('/api/v1/admin/complaints/c1/resolve');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ text: 'Remboursé' });
  });

  it('répond dans le fil', () => {
    svc.reply('c1', 'Bonjour').subscribe();
    const req = http.expectOne('/api/v1/admin/complaints/c1/messages');
    expect(req.request.body).toEqual({ body: 'Bonjour' });
  });
});
