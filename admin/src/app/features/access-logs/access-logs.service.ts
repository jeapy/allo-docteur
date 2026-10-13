import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { API_BASE_URL } from '../../core/config';
import { AccessLogRow, AccessOutcome, Page } from '../../core/models';

export interface AccessLogQuery {
  page: number;
  size: number;
  recordId?: string;
  actorId?: string;
  outcome?: AccessOutcome | '';
  from?: string; // instant ISO
  to?: string; // instant ISO
}

@Injectable({ providedIn: 'root' })
export class AccessLogsService {
  private readonly http = inject(HttpClient);
  private readonly base = inject(API_BASE_URL);

  list(query: AccessLogQuery): Observable<Page<AccessLogRow>> {
    let params = new HttpParams().set('page', query.page).set('size', query.size);
    if (query.recordId) params = params.set('recordId', query.recordId);
    if (query.actorId) params = params.set('actorId', query.actorId);
    if (query.outcome) params = params.set('outcome', query.outcome);
    if (query.from) params = params.set('from', query.from);
    if (query.to) params = params.set('to', query.to);
    return this.http.get<Page<AccessLogRow>>(`${this.base}/admin/medical-access-logs`, { params });
  }
}
