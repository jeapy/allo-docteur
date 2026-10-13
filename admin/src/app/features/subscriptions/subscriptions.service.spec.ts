import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { beforeEach, describe, expect, it } from 'vitest';
import { SubscriptionsService } from './subscriptions.service';

describe('SubscriptionsService', () => {
  let svc: SubscriptionsService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    svc = TestBed.inject(SubscriptionsService);
    http = TestBed.inject(HttpTestingController);
  });

  it('filtre les abonnements par statut', () => {
    svc.list({ page: 0, size: 20, status: 'ACTIVE' }).subscribe();
    const req = http.expectOne((r) => r.url === '/api/v1/admin/subscriptions');
    expect(req.request.params.get('status')).toBe('ACTIVE');
  });

  it("n'envoie pas de statut quand aucun n'est choisi", () => {
    svc.list({ page: 0, size: 20, status: null }).subscribe();
    const req = http.expectOne((r) => r.url === '/api/v1/admin/subscriptions');
    expect(req.request.params.has('status')).toBe(false);
  });

  it('met à jour une formule', () => {
    const payload = { name: 'Standard', description: null, price: 6000, durationDays: 30, commissionRate: null, active: true };
    svc.updatePlan('p1', payload).subscribe();
    const req = http.expectOne('/api/v1/admin/subscription-plans/p1');
    expect(req.request.method).toBe('PUT');
    expect(req.request.body).toEqual(payload);
  });
});
