import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { beforeEach, describe, expect, it } from 'vitest';
import { PharmaciesService } from './pharmacies.service';

describe('PharmaciesService', () => {
  let svc: PharmaciesService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    svc = TestBed.inject(PharmaciesService);
    http = TestBed.inject(HttpTestingController);
  });

  it('transmet recherche et filtre « de garde »', () => {
    svc.list({ page: 0, size: 20, q: '  plateau ', onDuty: true }).subscribe();
    const req = http.expectOne((r) => r.url === '/api/v1/admin/pharmacies');
    expect(req.request.params.get('q')).toBe('plateau');
    expect(req.request.params.get('onDuty')).toBe('true');
  });

  it("n'envoie pas les filtres vides", () => {
    svc.list({ page: 0, size: 20, q: ' ', onDuty: '' }).subscribe();
    const req = http.expectOne((r) => r.url === '/api/v1/admin/pharmacies');
    expect(req.request.params.has('q')).toBe(false);
    expect(req.request.params.has('onDuty')).toBe(false);
  });

  it('active et désactive via les routes dédiées', () => {
    svc.setActive('p1', false).subscribe();
    expect(http.expectOne('/api/v1/admin/pharmacies/p1/deactivate').request.method).toBe('POST');
    svc.setActive('p1', true).subscribe();
    http.expectOne('/api/v1/admin/pharmacies/p1/activate');
  });

  it('gère les pharmaciens rattachés', () => {
    svc.members('p1').subscribe();
    expect(http.expectOne('/api/v1/admin/pharmacies/p1/members').request.method).toBe('GET');
    svc.addMember('p1', 'u9').subscribe();
    expect(http.expectOne('/api/v1/admin/pharmacies/p1/members/u9').request.method).toBe('PUT');
    svc.removeMember('p1', 'u9').subscribe();
    expect(http.expectOne('/api/v1/admin/pharmacies/p1/members/u9').request.method).toBe('DELETE');
  });
});
