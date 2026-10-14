import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { beforeEach, describe, expect, it } from 'vitest';
import { AuditService } from './audit.service';

describe('AuditService', () => {
  let svc: AuditService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    svc = TestBed.inject(AuditService);
    http = TestBed.inject(HttpTestingController);
  });

  it('transmet pagination et filtres au serveur', () => {
    svc.list({ page: 1, size: 50, action: 'USER_STATUS_CHANGED', targetType: 'USER', actorId: 'u1' }).subscribe();
    const req = http.expectOne((r) => r.url === '/api/v1/admin/audit-logs');
    expect(req.request.params.get('page')).toBe('1');
    expect(req.request.params.get('size')).toBe('50');
    expect(req.request.params.get('action')).toBe('USER_STATUS_CHANGED');
    expect(req.request.params.get('targetType')).toBe('USER');
    expect(req.request.params.get('actorId')).toBe('u1');
    expect(req.request.params.has('targetId')).toBe(false);
  });

  it("n'envoie pas les filtres vides", () => {
    svc.list({ page: 0, size: 20, action: '', targetType: '' }).subscribe();
    const req = http.expectOne((r) => r.url === '/api/v1/admin/audit-logs');
    expect(req.request.params.has('action')).toBe(false);
    expect(req.request.params.has('targetType')).toBe(false);
  });

  it('charge la liste des actions', () => {
    let out: string[] = [];
    svc.actions().subscribe((a) => (out = a));
    http.expectOne('/api/v1/admin/audit-logs/actions').flush(['A', 'B']);
    expect(out).toEqual(['A', 'B']);
  });
});
