import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { API_BASE_URL } from '../../core/config';
import { Page, ReviewRow, ReviewStatus } from '../../core/models';

export interface ReviewQuery {
  page: number;
  size: number;
  status?: ReviewStatus | null;
}

@Injectable({ providedIn: 'root' })
export class ReviewsService {
  private readonly http = inject(HttpClient);
  private readonly base = `${inject(API_BASE_URL)}/admin/reviews`;

  list(q: ReviewQuery): Observable<Page<ReviewRow>> {
    let params = new HttpParams().set('page', q.page).set('size', q.size);
    if (q.status) params = params.set('status', q.status);
    return this.http.get<Page<ReviewRow>>(this.base, { params });
  }

  hide(id: string, reason: string): Observable<ReviewRow> {
    return this.http.post<ReviewRow>(`${this.base}/${id}/hide`, { reason });
  }

  show(id: string): Observable<ReviewRow> {
    return this.http.post<ReviewRow>(`${this.base}/${id}/show`, {});
  }
}
