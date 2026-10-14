import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { API_BASE_URL } from '../../core/config';
import { Page, Role, UserStatus, UserSummary } from '../../core/models';

export interface UserQuery {
  page: number;
  size: number;
  sort?: string; // ex. "createdAt,desc"
  q?: string;
  role?: Role | '';
  status?: UserStatus | '';
}

@Injectable({ providedIn: 'root' })
export class UsersService {
  private readonly http = inject(HttpClient);
  private readonly base = inject(API_BASE_URL);

  list(query: UserQuery): Observable<Page<UserSummary>> {
    let params = new HttpParams().set('page', query.page).set('size', query.size);
    if (query.sort) params = params.set('sort', query.sort);
    if (query.q) params = params.set('q', query.q);
    if (query.role) params = params.set('role', query.role);
    if (query.status) params = params.set('status', query.status);
    return this.http.get<Page<UserSummary>>(`${this.base}/admin/users`, { params });
  }

  setStatus(id: string, status: UserStatus): Observable<UserSummary> {
    return this.http.patch<UserSummary>(`${this.base}/admin/users/${id}/status`, { status });
  }

  /** Envoie à l'utilisateur un lien de réinitialisation (l'administration ne voit jamais le lien). */
  sendPasswordReset(id: string): Observable<void> {
    return this.http.post<void>(`${this.base}/admin/users/${id}/password-reset`, {});
  }
}
