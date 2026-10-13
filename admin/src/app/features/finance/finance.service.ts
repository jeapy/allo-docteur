import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { API_BASE_URL } from '../../core/config';
import {
  FinanceSummary, Page, PayableType, PaymentMethod, PaymentRow, PaymentStatus, WithdrawalRow, WithdrawalStatus,
} from '../../core/models';

export interface PaymentQuery {
  page: number;
  size: number;
  status?: PaymentStatus | null;
  method?: PaymentMethod | null;
  type?: PayableType | null;
}

export interface WithdrawalQuery {
  page: number;
  size: number;
  status?: WithdrawalStatus | null;
}

@Injectable({ providedIn: 'root' })
export class FinanceService {
  private readonly http = inject(HttpClient);
  private readonly base = inject(API_BASE_URL);

  summary(): Observable<FinanceSummary> {
    return this.http.get<FinanceSummary>(`${this.base}/admin/payments/summary`);
  }

  payments(q: PaymentQuery): Observable<Page<PaymentRow>> {
    let params = new HttpParams().set('page', q.page).set('size', q.size);
    if (q.status) params = params.set('status', q.status);
    if (q.method) params = params.set('method', q.method);
    if (q.type) params = params.set('type', q.type);
    return this.http.get<Page<PaymentRow>>(`${this.base}/admin/payments`, { params });
  }

  confirmTransfer(id: string): Observable<PaymentRow> {
    return this.http.post<PaymentRow>(`${this.base}/admin/payments/${id}/confirm-transfer`, {});
  }

  rejectTransfer(id: string, reason: string): Observable<PaymentRow> {
    return this.http.post<PaymentRow>(`${this.base}/admin/payments/${id}/reject-transfer`, { reason });
  }

  refund(id: string, reason: string): Observable<PaymentRow> {
    return this.http.post<PaymentRow>(`${this.base}/admin/payments/${id}/refund`, { reason });
  }

  withdrawals(q: WithdrawalQuery): Observable<Page<WithdrawalRow>> {
    let params = new HttpParams().set('page', q.page).set('size', q.size);
    if (q.status) params = params.set('status', q.status);
    return this.http.get<Page<WithdrawalRow>>(`${this.base}/admin/withdrawals`, { params });
  }

  payWithdrawal(id: string): Observable<WithdrawalRow> {
    return this.http.post<WithdrawalRow>(`${this.base}/admin/withdrawals/${id}/pay`, {});
  }

  rejectWithdrawal(id: string, reason: string): Observable<WithdrawalRow> {
    return this.http.post<WithdrawalRow>(`${this.base}/admin/withdrawals/${id}/reject`, { reason });
  }
}
