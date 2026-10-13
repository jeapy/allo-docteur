import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { API_BASE_URL } from '../../core/config';
import { Page, SosAlertRow, SosStatus, SosSummary } from '../../core/models';

export interface SosQuery {
  page: number;
  size: number;
  status?: SosStatus[];
}

@Injectable({ providedIn: 'root' })
export class SosService {
  private readonly http = inject(HttpClient);
  private readonly base = inject(API_BASE_URL);

  list(query: SosQuery): Observable<Page<SosAlertRow>> {
    let params = new HttpParams().set('page', query.page).set('size', query.size);
    for (const s of query.status ?? []) params = params.append('status', s);
    return this.http.get<Page<SosAlertRow>>(`${this.base}/admin/sos`, { params });
  }

  summary(): Observable<SosSummary> {
    return this.http.get<SosSummary>(`${this.base}/admin/sos/summary`);
  }

  cancel(id: string, reason: string): Observable<void> {
    return this.http.post<void>(`${this.base}/admin/sos/${id}/cancel`, { reason });
  }
}
