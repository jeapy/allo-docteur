import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { API_BASE_URL } from '../../core/config';
import { LicenseCheckStatus, Page, ProfessionalDetail, ProfessionalRow, VerificationStatus } from '../../core/models';

export interface ProfessionalQuery {
  page: number;
  size: number;
  status?: VerificationStatus | '';
  q?: string;
}

@Injectable({ providedIn: 'root' })
export class ProfessionalsService {
  private readonly http = inject(HttpClient);
  private readonly base = `${inject(API_BASE_URL)}/admin/professionals`;

  list(query: ProfessionalQuery): Observable<Page<ProfessionalRow>> {
    let params = new HttpParams().set('page', query.page).set('size', query.size);
    if (query.status) params = params.set('status', query.status);
    if (query.q) params = params.set('q', query.q);
    return this.http.get<Page<ProfessionalRow>>(this.base, { params });
  }

  get(id: string): Observable<ProfessionalDetail> {
    return this.http.get<ProfessionalDetail>(`${this.base}/${id}`);
  }

  approve(id: string): Observable<ProfessionalDetail> {
    return this.http.post<ProfessionalDetail>(`${this.base}/${id}/approve`, {});
  }

  reject(id: string, reason: string): Observable<ProfessionalDetail> {
    return this.http.post<ProfessionalDetail>(`${this.base}/${id}/reject`, { reason });
  }

  suspend(id: string, reason: string): Observable<ProfessionalDetail> {
    return this.http.post<ProfessionalDetail>(`${this.base}/${id}/suspend`, { reason });
  }

  /** Résultat de la vérification manuelle du numéro d'ordre auprès de l'ordre concerné. */
  checkLicense(id: string, result: Extract<LicenseCheckStatus, 'CONFIRMED' | 'NOT_FOUND'>, note?: string): Observable<ProfessionalDetail> {
    return this.http.post<ProfessionalDetail>(`${this.base}/${id}/license-check`, { result, note: note || null });
  }

  /** Les justificatifs exigent le jeton : on récupère un Blob puis on l'ouvre côté navigateur. */
  document(documentId: string): Observable<Blob> {
    return this.http.get(`${this.base.replace('/admin/professionals', '/professionals')}/documents/${documentId}/content`, {
      responseType: 'blob',
    });
  }
}
