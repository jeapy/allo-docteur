import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { API_BASE_URL } from '../../core/config';
import { CityRef, Page, PharmacyPayload, PharmacyRow } from '../../core/models';

export interface PharmacyQuery {
  page: number;
  size: number;
  q?: string;
  onDuty?: boolean | '';
}

@Injectable({ providedIn: 'root' })
export class PharmaciesService {
  private readonly http = inject(HttpClient);
  private readonly base = inject(API_BASE_URL);

  list(query: PharmacyQuery): Observable<Page<PharmacyRow>> {
    let params = new HttpParams().set('page', query.page).set('size', query.size);
    if (query.q?.trim()) params = params.set('q', query.q.trim());
    if (query.onDuty !== undefined && query.onDuty !== '') params = params.set('onDuty', query.onDuty);
    return this.http.get<Page<PharmacyRow>>(`${this.base}/admin/pharmacies`, { params });
  }

  create(payload: PharmacyPayload): Observable<PharmacyRow> {
    return this.http.post<PharmacyRow>(`${this.base}/admin/pharmacies`, payload);
  }

  update(id: string, payload: PharmacyPayload): Observable<PharmacyRow> {
    return this.http.put<PharmacyRow>(`${this.base}/admin/pharmacies/${id}`, payload);
  }

  setActive(id: string, active: boolean): Observable<PharmacyRow> {
    return this.http.post<PharmacyRow>(`${this.base}/admin/pharmacies/${id}/${active ? 'activate' : 'deactivate'}`, {});
  }

  cities(): Observable<CityRef[]> {
    return this.http.get<CityRef[]>(`${this.base}/reference/cities`);
  }
}
