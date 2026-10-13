import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { beforeEach, describe, expect, it } from 'vitest';
import { AccessLogsService } from './access-logs.service';

describe('AccessLogsService', () => {
  let svc: AccessLogsService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    svc = TestBed.inject(AccessLogsService);
    http = TestBed.inject(HttpTestingController);
  });

  it('transmet pagination et filtres au serveur', () => {
    svc.list({ page: 2, size: 50, recordId: 'r1', actorId: 'u1', outcome: 'DENIED', from: '2030-01-01T00:00:00.000Z' }).subscribe();
    const req = http.expectOne((r) => r.url === '/api/v1/admin/medical-access-logs');
    expect(req.request.params.get('page')).toBe('2');
    expect(req.request.params.get('size')).toBe('50');
    expect(req.request.params.get('recordId')).toBe('r1');
    expect(req.request.params.get('actorId')).toBe('u1');
    expect(req.request.params.get('outcome')).toBe('DENIED');
    expect(req.request.params.get('from')).toBe('2030-01-01T00:00:00.000Z');
    expect(req.request.params.has('to')).toBe(false);
  });

  it("n'envoie pas les filtres vides", () => {
    svc.list({ page: 0, size: 20, recordId: '', outcome: '' }).subscribe();
    const req = http.expectOne((r) => r.url === '/api/v1/admin/medical-access-logs');
    expect(req.request.params.has('recordId')).toBe(false);
    expect(req.request.params.has('outcome')).toBe(false);
  });
});
