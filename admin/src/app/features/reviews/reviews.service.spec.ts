import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { beforeEach, describe, expect, it } from 'vitest';
import { ReviewsService } from './reviews.service';

describe('ReviewsService', () => {
  let svc: ReviewsService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    svc = TestBed.inject(ReviewsService);
    http = TestBed.inject(HttpTestingController);
  });

  it('filtre par statut', () => {
    svc.list({ page: 0, size: 20, status: 'HIDDEN' }).subscribe();
    const req = http.expectOne((r) => r.url === '/api/v1/admin/reviews');
    expect(req.request.params.get('status')).toBe('HIDDEN');
  });

  it('masque un avis avec un motif', () => {
    svc.hide('r1', 'Injurieux').subscribe();
    const req = http.expectOne('/api/v1/admin/reviews/r1/hide');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ reason: 'Injurieux' });
  });

  it('rétablit un avis', () => {
    svc.show('r1').subscribe();
    expect(http.expectOne('/api/v1/admin/reviews/r1/show').request.method).toBe('POST');
  });
});
