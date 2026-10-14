import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { beforeEach, describe, expect, it } from 'vitest';
import { FacilitiesService } from './facilities.service';

describe('FacilitiesService', () => {
  let svc: FacilitiesService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    svc = TestBed.inject(FacilitiesService);
    http = TestBed.inject(HttpTestingController);
  });

  it('filtre par ville côté serveur', () => {
    svc.list({ page: 1, size: 10, cityId: 3 }).subscribe();
    const req = http.expectOne((r) => r.url === '/api/v1/admin/health-facilities');
    expect(req.request.params.get('cityId')).toBe('3');
    expect(req.request.params.get('page')).toBe('1');
  });

  it("n'envoie pas de ville vide", () => {
    svc.list({ page: 0, size: 20, cityId: '' }).subscribe();
    const req = http.expectOne((r) => r.url === '/api/v1/admin/health-facilities');
    expect(req.request.params.has('cityId')).toBe(false);
  });

  it('active et désactive', () => {
    svc.setActive('f1', false).subscribe();
    expect(http.expectOne('/api/v1/admin/health-facilities/f1/deactivate').request.method).toBe('POST');
    svc.setActive('f1', true).subscribe();
    expect(http.expectOne('/api/v1/admin/health-facilities/f1/activate').request.method).toBe('POST');
  });
});
