import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { API_BASE_URL } from '../../core/config';
import { AuditRow, Page } from '../../core/models';

export interface AuditQuery {
  page: number;
  size: number;
  action?: string;
  targetType?: string;
  targetId?: string;
  actorId?: string;
  from?: string; // instant ISO
  to?: string; // instant ISO
}

@Injectable({ providedIn: 'root' })
export class AuditService {
  private readonly http = inject(HttpClient);
  private readonly base = inject(API_BASE_URL);

  list(query: AuditQuery): Observable<Page<AuditRow>> {
    let params = new HttpParams().set('page', query.page).set('size', query.size);
    for (const key of ['action', 'targetType', 'targetId', 'actorId', 'from', 'to'] as const) {
      const value = query[key];
      if (value) params = params.set(key, value);
    }
    return this.http.get<Page<AuditRow>>(`${this.base}/admin/audit-logs`, { params });
  }

  actions(): Observable<string[]> {
    return this.http.get<string[]>(`${this.base}/admin/audit-logs/actions`);
  }
}
