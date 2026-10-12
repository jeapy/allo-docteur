import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { describe, it, expect, beforeEach } from 'vitest';
import { ProfessionalsService } from './professionals.service';

describe('ProfessionalsService', () => {
  let svc: ProfessionalsService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    svc = TestBed.inject(ProfessionalsService);
    http = TestBed.inject(HttpTestingController);
  });

  it('transmet pagination et filtres au serveur', () => {
    svc.list({ page: 2, size: 10, status: 'PENDING', q: 'koné' }).subscribe();
    const req = http.expectOne((r) => r.url === '/api/v1/admin/professionals');
    expect(req.request.params.get('page')).toBe('2');
    expect(req.request.params.get('size')).toBe('10');
    expect(req.request.params.get('status')).toBe('PENDING');
    expect(req.request.params.get('q')).toBe('koné');
  });

  it("n'envoie pas les filtres vides", () => {
    svc.list({ page: 0, size: 20, status: '', q: '' }).subscribe();
    const req = http.expectOne((r) => r.url === '/api/v1/admin/professionals');
    expect(req.request.params.has('status')).toBe(false);
    expect(req.request.params.has('q')).toBe(false);
  });

  it('envoie le motif lors du rejet', () => {
    svc.reject('abc', 'Illisible').subscribe();
    const req = http.expectOne('/api/v1/admin/professionals/abc/reject');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ reason: 'Illisible' });
  });

  it('télécharge un justificatif via le chemin public protégé', () => {
    svc.document('d1').subscribe();
    const req = http.expectOne('/api/v1/professionals/documents/d1/content');
    expect(req.request.responseType).toBe('blob');
  });
});
