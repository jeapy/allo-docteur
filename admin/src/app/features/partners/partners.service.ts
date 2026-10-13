import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { API_BASE_URL } from '../../core/config';
import { CityRef, Page, PartnerPayload, PartnerRow, PartnerStatus, PartnerType } from '../../core/models';

export interface PartnerQuery {
  page: number;
  size: number;
  status?: PartnerStatus | null;
  type?: PartnerType | null;
}

@Injectable({ providedIn: 'root' })
export class PartnersService {
  private readonly http = inject(HttpClient);
  private readonly root = inject(API_BASE_URL);
  private readonly base = `${this.root}/admin/partners`;

  list(q: PartnerQuery): Observable<Page<PartnerRow>> {
    let params = new HttpParams().set('page', q.page).set('size', q.size);
    if (q.status) params = params.set('status', q.status);
    if (q.type) params = params.set('type', q.type);
    return this.http.get<Page<PartnerRow>>(this.base, { params });
  }

  create(payload: PartnerPayload): Observable<PartnerRow> {
    return this.http.post<PartnerRow>(this.base, payload);
  }

  update(id: string, payload: PartnerPayload): Observable<PartnerRow> {
    return this.http.put<PartnerRow>(`${this.base}/${id}`, payload);
  }

  setActive(id: string, active: boolean): Observable<PartnerRow> {
    return this.http.post<PartnerRow>(`${this.base}/${id}/${active ? 'activate' : 'deactivate'}`, {});
  }

  cities(): Observable<CityRef[]> {
    return this.http.get<CityRef[]>(`${this.root}/reference/cities`);
  }
}
