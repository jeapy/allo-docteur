import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { beforeEach, describe, expect, it } from 'vitest';
import { PartnersService } from './partners.service';

describe('PartnersService', () => {
  let svc: PartnersService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    svc = TestBed.inject(PartnersService);
    http = TestBed.inject(HttpTestingController);
  });

  it('filtre par type et statut', () => {
    svc.list({ page: 0, size: 20, status: 'ACTIVE', type: 'LAB' }).subscribe();
    const req = http.expectOne((r) => r.url === '/api/v1/admin/partners');
    expect(req.request.params.get('type')).toBe('LAB');
    expect(req.request.params.get('status')).toBe('ACTIVE');
  });

  it('active et désactive', () => {
    svc.setActive('p1', true).subscribe();
    expect(http.expectOne('/api/v1/admin/partners/p1/activate').request.method).toBe('POST');
    svc.setActive('p1', false).subscribe();
    expect(http.expectOne('/api/v1/admin/partners/p1/deactivate').request.method).toBe('POST');
  });

  it('crée un partenaire', () => {
    const payload = {
      name: 'Clinique', type: 'CLINIC' as const, cityId: 1, address: null, contactEmail: null, contactPhone: null,
      contractStartDate: null,
    };
    svc.create(payload).subscribe();
    const req = http.expectOne('/api/v1/admin/partners');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual(payload);
  });
});
