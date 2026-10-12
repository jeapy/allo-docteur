import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { API_BASE_URL } from '../../core/config';
import { AppointmentRow, AppointmentStatus, AppointmentType, Page } from '../../core/models';

export interface AppointmentQuery {
  page: number;
  size: number;
  sort?: string; // ex. "startAt,desc"
  status?: AppointmentStatus | '';
  type?: AppointmentType | '';
  from?: string; // instant ISO, inclus
  to?: string; // instant ISO, exclu
}

@Injectable({ providedIn: 'root' })
export class AppointmentsService {
  private readonly http = inject(HttpClient);
  private readonly base = inject(API_BASE_URL);

  list(query: AppointmentQuery): Observable<Page<AppointmentRow>> {
    let params = new HttpParams().set('page', query.page).set('size', query.size);
    if (query.sort) params = params.set('sort', query.sort);
    if (query.status) params = params.set('status', query.status);
    if (query.type) params = params.set('type', query.type);
    if (query.from) params = params.set('from', query.from);
    if (query.to) params = params.set('to', query.to);
    return this.http.get<Page<AppointmentRow>>(`${this.base}/admin/appointments`, { params });
  }

  cancel(id: string, reason: string): Observable<AppointmentRow> {
    return this.http.post<AppointmentRow>(`${this.base}/appointments/${id}/cancel`, { reason });
  }
}
