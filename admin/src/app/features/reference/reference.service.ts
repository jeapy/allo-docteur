import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { API_BASE_URL } from '../../core/config';
import { CityAdmin, ProfessionalTypeAdmin, SpecialtyAdmin } from '../../core/models';

@Injectable({ providedIn: 'root' })
export class ReferenceAdminService {
  private readonly http = inject(HttpClient);
  private readonly root = `${inject(API_BASE_URL)}/admin/reference`;

  cities(): Observable<CityAdmin[]> {
    return this.http.get<CityAdmin[]>(`${this.root}/cities`);
  }
  createCity(name: string): Observable<CityAdmin> {
    return this.http.post<CityAdmin>(`${this.root}/cities`, { name });
  }
  renameCity(id: number, name: string): Observable<CityAdmin> {
    return this.http.put<CityAdmin>(`${this.root}/cities/${id}`, { name });
  }
  setCityActive(id: number, active: boolean): Observable<CityAdmin> {
    return this.http.post<CityAdmin>(`${this.root}/cities/${id}/${active ? 'activate' : 'deactivate'}`, {});
  }
  createDistrict(cityId: number, name: string): Observable<CityAdmin> {
    return this.http.post<CityAdmin>(`${this.root}/cities/${cityId}/districts`, { name });
  }
  renameDistrict(id: number, name: string): Observable<CityAdmin> {
    return this.http.put<CityAdmin>(`${this.root}/districts/${id}`, { name });
  }
  setDistrictActive(id: number, active: boolean): Observable<CityAdmin> {
    return this.http.post<CityAdmin>(`${this.root}/districts/${id}/${active ? 'activate' : 'deactivate'}`, {});
  }

  specialties(): Observable<SpecialtyAdmin[]> {
    return this.http.get<SpecialtyAdmin[]>(`${this.root}/specialties`);
  }
  createSpecialty(code: string, name: string): Observable<SpecialtyAdmin> {
    return this.http.post<SpecialtyAdmin>(`${this.root}/specialties`, { code, name });
  }
  renameSpecialty(id: number, name: string): Observable<SpecialtyAdmin> {
    return this.http.put<SpecialtyAdmin>(`${this.root}/specialties/${id}`, { name });
  }
  setSpecialtyActive(id: number, active: boolean): Observable<SpecialtyAdmin> {
    return this.http.post<SpecialtyAdmin>(`${this.root}/specialties/${id}/${active ? 'activate' : 'deactivate'}`, {});
  }

  types(): Observable<ProfessionalTypeAdmin[]> {
    return this.http.get<ProfessionalTypeAdmin[]>(`${this.root}/professional-types`);
  }
  createType(code: string, name: string): Observable<ProfessionalTypeAdmin> {
    return this.http.post<ProfessionalTypeAdmin>(`${this.root}/professional-types`, { code, name });
  }
  renameType(id: number, name: string): Observable<ProfessionalTypeAdmin> {
    return this.http.put<ProfessionalTypeAdmin>(`${this.root}/professional-types/${id}`, { name });
  }
}
