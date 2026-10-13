import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { API_BASE_URL } from '../../core/config';
import { ComplaintDetail, ComplaintRow, ComplaintStatus, ComplaintSummary, Page } from '../../core/models';

export interface ComplaintQuery {
  page: number;
  size: number;
  status?: ComplaintStatus | null;
}

@Injectable({ providedIn: 'root' })
export class ComplaintsService {
  private readonly http = inject(HttpClient);
  private readonly base = `${inject(API_BASE_URL)}/admin/complaints`;

  list(q: ComplaintQuery): Observable<Page<ComplaintRow>> {
    let params = new HttpParams().set('page', q.page).set('size', q.size);
    if (q.status) params = params.set('status', q.status);
    return this.http.get<Page<ComplaintRow>>(this.base, { params });
  }

  summary(): Observable<ComplaintSummary> {
    return this.http.get<ComplaintSummary>(`${this.base}/summary`);
  }

  get(id: string): Observable<ComplaintDetail> {
    return this.http.get<ComplaintDetail>(`${this.base}/${id}`);
  }

  take(id: string): Observable<ComplaintDetail> {
    return this.http.post<ComplaintDetail>(`${this.base}/${id}/take`, {});
  }

  reply(id: string, body: string): Observable<ComplaintDetail> {
    return this.http.post<ComplaintDetail>(`${this.base}/${id}/messages`, { body });
  }

  resolve(id: string, text: string): Observable<ComplaintDetail> {
    return this.http.post<ComplaintDetail>(`${this.base}/${id}/resolve`, { text });
  }

  reject(id: string, text: string): Observable<ComplaintDetail> {
    return this.http.post<ComplaintDetail>(`${this.base}/${id}/reject`, { text });
  }
}
