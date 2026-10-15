import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { beforeEach, describe, expect, it } from 'vitest';
import { ApplicationDocument, ApplicationPayload, ApplyService, fileProblem } from './apply.service';

function doc(type: ApplicationDocument['type'], mime = 'application/pdf', size = 10): ApplicationDocument {
  const file = new File([new Uint8Array(size)], 'f.pdf', { type: mime });
  return { type, file };
}

describe('fileProblem', () => {
  it('exige au moins un fichier, dont un justificatif LICENSE', () => {
    expect(fileProblem([])).toBe('empty');
    expect(fileProblem([doc('DIPLOMA')])).toBe('licenseMissing');
    expect(fileProblem([doc('LICENSE')])).toBeNull();
  });

  it('refuse les formats, tailles et nombres hors limites', () => {
    expect(fileProblem([doc('LICENSE', 'text/html')])).toBe('unsupported');
    expect(fileProblem([doc('LICENSE', 'application/pdf', 5 * 1024 * 1024 + 1)])).toBe('tooLarge');
    expect(fileProblem(Array.from({ length: 6 }, () => doc('LICENSE')))).toBe('tooMany');
    const big = 4 * 1024 * 1024;
    expect(fileProblem([doc('LICENSE', 'image/png', big), doc('ID_CARD', 'image/jpeg', big), doc('OTHER', 'application/pdf', big)]))
      .toBe('totalTooLarge');
  });
});

describe('ApplyService', () => {
  let svc: ApplyService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    svc = TestBed.inject(ApplyService);
    http = TestBed.inject(HttpTestingController);
  });

  it("contrôle le numéro d'ordre via l'endpoint public", () => {
    svc.checkLicense(3, 'CI-123').subscribe();
    const req = http.expectOne((r) => r.url === '/api/v1/public/license-check');
    expect(req.request.params.get('typeId')).toBe('3');
    expect(req.request.params.get('number')).toBe('CI-123');
  });

  it('envoie la candidature en multipart : JSON, types et fichiers alignés', () => {
    const payload = { acceptTerms: true } as ApplicationPayload;
    svc.submit(payload, [doc('LICENSE'), doc('DIPLOMA')]).subscribe();
    const req = http.expectOne('/api/v1/public/professional-applications');
    const body = req.request.body as FormData;
    expect(req.request.method).toBe('POST');
    expect(body.getAll('types')).toEqual(['LICENSE', 'DIPLOMA']);
    expect(body.getAll('files')).toHaveLength(2);
    expect((body.get('application') as Blob).type).toBe('application/json');
  });
});
