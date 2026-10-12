import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { beforeEach, describe, expect, it } from 'vitest';
import { AppointmentsService } from './appointments.service';

describe('AppointmentsService', () => {
  let svc: AppointmentsService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    svc = TestBed.inject(AppointmentsService);
    http = TestBed.inject(HttpTestingController);
  });

  it('transmet pagination, tri et filtres au serveur', () => {
    svc.list({ page: 1, size: 20, sort: 'startAt,asc', status: 'CONFIRMED', type: 'HOME_VISIT', from: '2030-01-07T00:00:00.000Z' }).subscribe();
    const req = http.expectOne((r) => r.url === '/api/v1/admin/appointments');
    expect(req.request.params.get('page')).toBe('1');
    expect(req.request.params.get('sort')).toBe('startAt,asc');
    expect(req.request.params.get('status')).toBe('CONFIRMED');
    expect(req.request.params.get('type')).toBe('HOME_VISIT');
    expect(req.request.params.get('from')).toBe('2030-01-07T00:00:00.000Z');
    expect(req.request.params.has('to')).toBe(false);
  });

  it("n'envoie pas les filtres vides", () => {
    svc.list({ page: 0, size: 20, status: '', type: '' }).subscribe();
    const req = http.expectOne((r) => r.url === '/api/v1/admin/appointments');
    expect(req.request.params.has('status')).toBe(false);
    expect(req.request.params.has('type')).toBe(false);
  });

  it("envoie le motif lors de l'annulation", () => {
    svc.cancel('a1', 'Doublon').subscribe();
    const req = http.expectOne('/api/v1/appointments/a1/cancel');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ reason: 'Doublon' });
  });
});
