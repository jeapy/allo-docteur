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

export type VerificationStatus = 'PENDING' | 'APPROVED' | 'REJECTED' | 'SUSPENDED';

export interface TypeRef { id: number; code: string; label: string }
export interface Ref { id: number; name: string }

export interface ProfessionalRow {
  id: string;
  firstName: string | null;
  lastName: string | null;
  email: string;
  type: TypeRef;
  licenseNumber: string;
  city: string;
  verificationStatus: VerificationStatus;
  createdAt: string;
}

export interface ProfessionalDocument {
  id: string;
  type: 'LICENSE' | 'DIPLOMA' | 'ID_CARD' | 'OTHER';
  filename: string;
  contentType: string;
  sizeBytes: number;
  uploadedAt: string;
}

export interface ProfessionalDetail {
  id: string;
  firstName: string | null;
  lastName: string | null;
  email?: string;
  type: TypeRef;
  qualification: string;
  licenseNumber: string;
  yearsExperience: number | null;
  bio: string | null;
  city: Ref;
  district: Ref | null;
  consultationFee: number | null;
  homeVisitFee: number | null;
  currency: string;
  offersTeleconsultation: boolean;
  offersHomeVisit: boolean;
  specialties: Ref[];
  coverageZones: { city: string; district: string | null }[];
  verificationStatus?: VerificationStatus;
  rejectionReason?: string | null;
  documents?: ProfessionalDocument[];
  createdAt?: string;
}
