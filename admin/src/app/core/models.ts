export type Role = 'PATIENT' | 'PROFESSIONAL' | 'ADMIN';
export type UserStatus = 'ACTIVE' | 'SUSPENDED' | 'PENDING';

export interface CurrentUser {
  id: string;
  email: string;
  firstName: string | null;
  lastName: string | null;
  role: Role;
  adminLevel?: 'SUPER' | 'SUPPORT' | 'FINANCE' | null;
}

export interface TokenResponse {
  accessToken: string;
  expiresIn: number;
}

export type LoginResponse =
  | { status: 'AUTHENTICATED'; accessToken: string; expiresIn: number; user: CurrentUser }
  | { status: 'MFA_REQUIRED'; mfaToken: string };

export interface Page<T> {
  content: T[];
  totalElements: number;
  number: number;
  size: number;
}

export interface UserSummary {
  id: string;
  email: string;
  phone: string | null;
  firstName: string | null;
  lastName: string | null;
  role: Role;
  status: UserStatus;
  createdAt: string;
}

/** RFC 7807 ProblemDetail, avec un code d'erreur stable ajouté par le backend. */
export interface ProblemDetail {
  type?: string;
  title?: string;
  status?: number;
  detail?: string;
  code?: string;
}
