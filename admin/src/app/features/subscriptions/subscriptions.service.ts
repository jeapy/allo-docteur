import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { API_BASE_URL } from '../../core/config';
import { Page, PlanPayload, PlanRow, SubscriptionRow, SubscriptionStatus } from '../../core/models';

export interface SubscriptionQuery {
  page: number;
  size: number;
  status?: SubscriptionStatus | null;
}

@Injectable({ providedIn: 'root' })
export class SubscriptionsService {
  private readonly http = inject(HttpClient);
  private readonly base = inject(API_BASE_URL);

  list(q: SubscriptionQuery): Observable<Page<SubscriptionRow>> {
    let params = new HttpParams().set('page', q.page).set('size', q.size);
    if (q.status) params = params.set('status', q.status);
    return this.http.get<Page<SubscriptionRow>>(`${this.base}/admin/subscriptions`, { params });
  }

  plans(): Observable<PlanRow[]> {
    return this.http.get<PlanRow[]>(`${this.base}/admin/subscription-plans`);
  }

  updatePlan(id: string, payload: PlanPayload): Observable<PlanRow> {
    return this.http.put<PlanRow>(`${this.base}/admin/subscription-plans/${id}`, payload);
  }
}
