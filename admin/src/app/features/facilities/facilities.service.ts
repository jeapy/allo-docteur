import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { API_BASE_URL } from '../../core/config';
import { CityRef, FacilityPayload, FacilityRow, Page } from '../../core/models';

export interface FacilityQuery {
  page: number;
  size: number;
  cityId?: number | '';
}

@Injectable({ providedIn: 'root' })
export class FacilitiesService {
  private readonly http = inject(HttpClient);
  private readonly base = inject(API_BASE_URL);

  list(query: FacilityQuery): Observable<Page<FacilityRow>> {
    let params = new HttpParams().set('page', query.page).set('size', query.size);
    if (query.cityId) params = params.set('cityId', query.cityId);
    return this.http.get<Page<FacilityRow>>(`${this.base}/admin/health-facilities`, { params });
  }

  create(payload: FacilityPayload): Observable<FacilityRow> {
    return this.http.post<FacilityRow>(`${this.base}/admin/health-facilities`, payload);
  }

  update(id: string, payload: FacilityPayload): Observable<FacilityRow> {
    return this.http.put<FacilityRow>(`${this.base}/admin/health-facilities/${id}`, payload);
  }

  setActive(id: string, active: boolean): Observable<FacilityRow> {
    return this.http.post<FacilityRow>(`${this.base}/admin/health-facilities/${id}/${active ? 'activate' : 'deactivate'}`, {});
  }

  cities(): Observable<CityRef[]> {
    return this.http.get<CityRef[]>(`${this.base}/reference/cities`);
  }
}
