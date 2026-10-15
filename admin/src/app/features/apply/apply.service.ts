import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { API_BASE_URL } from '../../core/config';
import { CityRef, TypeRef } from '../../core/models';

export type DocumentKind = 'LICENSE' | 'DIPLOMA' | 'ID_CARD' | 'OTHER';

export type LicenseOutcome =
  | 'INVALID_FORMAT'
  | 'ALREADY_REGISTERED'
  | 'FORMAT_VALID'
  | 'REGISTRY_CONFIRMED'
  | 'REGISTRY_NOT_FOUND';

export interface LicenseCheckResult {
  status: LicenseOutcome;
  message: string;
}

export interface ApplicationPayload {
  account: { email: string; password: string; firstName: string; lastName: string; phone: string };
  profile: {
    professionalTypeId: number;
    qualification: string;
    licenseNumber: string;
    yearsExperience: number | null;
    bio: string | null;
    cityId: number;
    districtId: number | null;
    consultationFee: number | null;
    homeVisitFee: number | null;
    offersTeleconsultation: boolean;
    offersHomeVisit: boolean;
    specialtyIds: number[];
    coverageZones: { cityId: number; districtId: number | null }[];
  };
  acceptTerms: boolean;
}

export interface ApplicationDocument {
  type: DocumentKind;
  file: File;
}

export interface ApplicationReceipt {
  reference: string;
  email: string;
  status: string;
}

export interface SpecialtyRef { id: number; code: string; name: string }

/** Limites alignées sur le backend (5 fichiers, 5 Mo chacun, 12 Mo par requête multipart). */
export const MAX_FILES = 5;
export const MAX_FILE_BYTES = 5 * 1024 * 1024;
export const MAX_TOTAL_BYTES = 11 * 1024 * 1024;
export const ACCEPTED_TYPES = ['application/pdf', 'image/jpeg', 'image/png'];

export type FileProblem = 'tooMany' | 'tooLarge' | 'totalTooLarge' | 'unsupported' | 'licenseMissing' | 'empty';

/** Premier problème bloquant d'une liste de justificatifs, ou null si elle est acceptable. */
export function fileProblem(docs: readonly ApplicationDocument[]): FileProblem | null {
  if (docs.length === 0) return 'empty';
  if (docs.length > MAX_FILES) return 'tooMany';
  if (docs.some((d) => !ACCEPTED_TYPES.includes(d.file.type))) return 'unsupported';
  if (docs.some((d) => d.file.size > MAX_FILE_BYTES)) return 'tooLarge';
  if (docs.reduce((n, d) => n + d.file.size, 0) > MAX_TOTAL_BYTES) return 'totalTooLarge';
  if (!docs.some((d) => d.type === 'LICENSE')) return 'licenseMissing';
  return null;
}

/** Candidature d'un professionnel : endpoints publics (sans JWT). */
@Injectable({ providedIn: 'root' })
export class ApplyService {
  private readonly http = inject(HttpClient);
  private readonly base = inject(API_BASE_URL);

  types(): Observable<TypeRef[]> {
    return this.http.get<TypeRef[]>(`${this.base}/reference/professional-types`);
  }

  specialties(): Observable<SpecialtyRef[]> {
    return this.http.get<SpecialtyRef[]>(`${this.base}/reference/specialties`);
  }

  cities(): Observable<CityRef[]> {
    return this.http.get<CityRef[]>(`${this.base}/reference/cities`);
  }

  checkLicense(typeId: number, number: string): Observable<LicenseCheckResult> {
    const params = new HttpParams().set('typeId', typeId).set('number', number);
    return this.http.get<LicenseCheckResult>(`${this.base}/public/license-check`, { params });
  }

  submit(application: ApplicationPayload, documents: readonly ApplicationDocument[]): Observable<ApplicationReceipt> {
    const form = new FormData();
    form.append('application', new Blob([JSON.stringify(application)], { type: 'application/json' }));
    for (const d of documents) {
      form.append('types', d.type);
      form.append('files', d.file, d.file.name);
    }
    return this.http.post<ApplicationReceipt>(`${this.base}/public/professional-applications`, form);
  }
}
