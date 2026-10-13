import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { beforeEach, describe, expect, it } from 'vitest';
import { SosService } from './sos.service';

describe('SosService', () => {
  let svc: SosService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    svc = TestBed.inject(SosService);
    http = TestBed.inject(HttpTestingController);
  });

  it('répète le paramètre status pour chaque statut choisi', () => {
    svc.list({ page: 0, size: 20, status: ['OPEN', 'ACCEPTED'] }).subscribe();
    const req = http.expectOne((r) => r.url === '/api/v1/admin/sos');
    expect(req.request.params.getAll('status')).toEqual(['OPEN', 'ACCEPTED']);
  });

  it("n'envoie pas de filtre quand aucun statut n'est choisi", () => {
    svc.list({ page: 1, size: 50 }).subscribe();
    const req = http.expectOne((r) => r.url === '/api/v1/admin/sos');
    expect(req.request.params.has('status')).toBe(false);
    expect(req.request.params.get('page')).toBe('1');
  });

  it('envoie le motif lors de l’annulation', () => {
    svc.cancel('s1', 'Doublon').subscribe();
    const req = http.expectOne('/api/v1/admin/sos/s1/cancel');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ reason: 'Doublon' });
  });
});
