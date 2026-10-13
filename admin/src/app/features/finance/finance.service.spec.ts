import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { beforeEach, describe, expect, it } from 'vitest';
import { FinanceService } from './finance.service';

describe('FinanceService', () => {
  let svc: FinanceService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    svc = TestBed.inject(FinanceService);
    http = TestBed.inject(HttpTestingController);
  });

  it("n'envoie que les filtres renseignés", () => {
    svc.payments({ page: 2, size: 10, status: 'PENDING', method: null }).subscribe();
    const req = http.expectOne((r) => r.url === '/api/v1/admin/payments');
    expect(req.request.params.get('status')).toBe('PENDING');
    expect(req.request.params.has('method')).toBe(false);
    expect(req.request.params.has('type')).toBe(false);
    expect(req.request.params.get('page')).toBe('2');
  });

  it('confirme un virement', () => {
    svc.confirmTransfer('p1').subscribe();
    const req = http.expectOne('/api/v1/admin/payments/p1/confirm-transfer');
    expect(req.request.method).toBe('POST');
  });

  it('transmet le motif du rejet et du remboursement', () => {
    svc.rejectTransfer('p1', 'Non reçu').subscribe();
    expect(http.expectOne('/api/v1/admin/payments/p1/reject-transfer').request.body).toEqual({ reason: 'Non reçu' });
    svc.refund('p2', 'Litige').subscribe();
    expect(http.expectOne('/api/v1/admin/payments/p2/refund').request.body).toEqual({ reason: 'Litige' });
    svc.rejectWithdrawal('w1', 'Numéro invalide').subscribe();
    expect(http.expectOne('/api/v1/admin/withdrawals/w1/reject').request.body).toEqual({ reason: 'Numéro invalide' });
  });

  it('filtre les retraits par statut', () => {
    svc.withdrawals({ page: 0, size: 20, status: 'REQUESTED' }).subscribe();
    const req = http.expectOne((r) => r.url === '/api/v1/admin/withdrawals');
    expect(req.request.params.get('status')).toBe('REQUESTED');
    svc.payWithdrawal('w1').subscribe();
    expect(http.expectOne('/api/v1/admin/withdrawals/w1/pay').request.method).toBe('POST');
  });
});
