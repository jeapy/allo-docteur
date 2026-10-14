import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { beforeEach, describe, expect, it } from 'vitest';
import { ReferenceAdminService } from './reference.service';

describe('ReferenceAdminService', () => {
  let svc: ReferenceAdminService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    svc = TestBed.inject(ReferenceAdminService);
    http = TestBed.inject(HttpTestingController);
  });

  it('crée et renomme une ville, puis un quartier', () => {
    svc.createCity('Bouaké').subscribe();
    expect(http.expectOne('/api/v1/admin/reference/cities').request.body).toEqual({ name: 'Bouaké' });
    svc.renameCity(2, 'Bouaké 2').subscribe();
    expect(http.expectOne('/api/v1/admin/reference/cities/2').request.method).toBe('PUT');
    svc.createDistrict(2, 'Centre').subscribe();
    expect(http.expectOne('/api/v1/admin/reference/cities/2/districts').request.body).toEqual({ name: 'Centre' });
    svc.renameDistrict(9, 'Nord').subscribe();
    expect(http.expectOne('/api/v1/admin/reference/districts/9').request.method).toBe('PUT');
  });

  it('active et désactive via les routes dédiées', () => {
    svc.setCityActive(1, false).subscribe();
    http.expectOne('/api/v1/admin/reference/cities/1/deactivate');
    svc.setDistrictActive(4, true).subscribe();
    http.expectOne('/api/v1/admin/reference/districts/4/activate');
    svc.setSpecialtyActive(5, false).subscribe();
    http.expectOne('/api/v1/admin/reference/specialties/5/deactivate');
  });

  it('crée spécialités et types avec leur code', () => {
    svc.createSpecialty('CARDIO', 'Cardiologie').subscribe();
    expect(http.expectOne('/api/v1/admin/reference/specialties').request.body).toEqual({ code: 'CARDIO', name: 'Cardiologie' });
    svc.createType('DENTIST', 'Dentiste').subscribe();
    expect(http.expectOne('/api/v1/admin/reference/professional-types').request.body).toEqual({ code: 'DENTIST', name: 'Dentiste' });
    svc.renameType(3, 'Dentiste.').subscribe();
    expect(http.expectOne('/api/v1/admin/reference/professional-types/3').request.method).toBe('PUT');
  });
});
