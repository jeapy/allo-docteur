export type Role = 'PATIENT' | 'PROFESSIONAL' | 'ADMIN';
export type UserStatus = 'ACTIVE' | 'SUSPENDED' | 'PENDING';

export interface CurrentUser {
  id: string;
  email: string;
  firstName: string | null;
  lastName: string | null;
  role: Role;
  adminLevel?: AdminLevel | null;
  mfaEnabled: boolean;
}

export interface TotpSetup {
  secret: string;
  otpauthUri: string;
}

export interface RecoveryCodes {
  recoveryCodes: string[];
}

export interface TokenResponse {
  accessToken: string;
  expiresIn: number;
}

export type LoginResponse =
  | { status: 'AUTHENTICATED'; accessToken: string; expiresIn: number; user: CurrentUser }
  | { status: 'MFA_REQUIRED'; mfaToken: string };

export type AdminLevel = 'SUPER' | 'SUPPORT' | 'FINANCE';

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

export type AppointmentStatus = 'REQUESTED' | 'CONFIRMED' | 'IN_PROGRESS' | 'COMPLETED' | 'CANCELLED' | 'NO_SHOW';
export type AppointmentType = 'TELECONSULTATION_VIDEO' | 'TELECONSULTATION_AUDIO' | 'CHAT' | 'HOME_VISIT';

/** Vue administrateur : le détail du motif et la date de naissance ne sont jamais exposés. */
export interface AppointmentRow {
  id: string;
  status: AppointmentStatus;
  type: AppointmentType;
  reason: string;
  startAt: string;
  endAt: string;
  fee: number;
  currency: string;
  address?: string | null;
  patient: { id: string; name: string };
  beneficiaryName: string;
  professional: { id: string; name: string; qualification?: string | null; typeLabel?: string | null };
  cancelReason?: string | null;
  createdAt: string;
}

export type AccessOutcome = 'GRANTED' | 'DENIED';

/** Journal d'accès aux dossiers médicaux : métadonnées uniquement, jamais de contenu médical. */
export interface AccessLogRow {
  id: string;
  recordId: string;
  actorId: string;
  actorName: string;
  actorRole: Role;
  action: string;
  targetId?: string | null;
  outcome: AccessOutcome;
  reason?: string | null;
  ipAddress?: string | null;
  occurredAt: string;
}

/** Journal d'audit général. */
export interface AuditRow {
  id: string;
  actorId?: string | null;
  actorEmail?: string | null;
  action: string;
  targetType?: string | null;
  targetId?: string | null;
  ipAddress?: string | null;
  details?: string | null;
  createdAt: string;
}

export type SosStatus = 'OPEN' | 'ACCEPTED' | 'RESOLVED' | 'CANCELLED';
export type SosCategory = 'MEDICAL' | 'ACCIDENT' | 'PREGNANCY' | 'CHILD' | 'OTHER';

/** Suivi des urgences : ni description médicale ni coordonnées exactes. */
export interface SosAlertRow {
  id: string;
  status: SosStatus;
  category: SosCategory;
  accountId: string;
  patientName: string;
  cityId?: number | null;
  city?: string | null;
  professional?: { id: string; name: string } | null;
  appointmentId?: string | null;
  createdAt: string;
  acceptedAt?: string | null;
  resolvedAt?: string | null;
  cancelReason?: string | null;
  waitingMinutes: number;
}

export interface SosSummary {
  open: number;
  accepted: number;
}

export interface PharmacyRow {
  id: string;
  name: string;
  cityId: number;
  city: string;
  districtId?: number | null;
  district?: string | null;
  address: string;
  phone: string;
  latitude?: number | null;
  longitude?: number | null;
  openingHours?: string | null;
  onDuty: boolean;
  offersDelivery: boolean;
  deliveryFee: number;
  currency: string;
  active: boolean;
}

export interface PharmacyMember {
  userId: string;
  name: string;
  email: string | null;
  addedAt: string;
}

export interface PharmacyPayload {
  name: string;
  cityId: number;
  districtId: number | null;
  address: string;
  phone: string;
  latitude: number | null;
  longitude: number | null;
  openingHours: string | null;
  onDuty: boolean;
  offersDelivery: boolean;
  deliveryFee: number | null;
}

export interface DistrictRef {
  id: number;
  name: string;
}

export interface CityRef {
  id: number;
  name: string;
  districts?: DistrictRef[];
}

export type FacilityType = 'HOSPITAL' | 'CLINIC' | 'HEALTH_CENTER';

export interface FacilityRow {
  id: string;
  name: string;
  type: FacilityType;
  cityId: number;
  city: string;
  districtId?: number | null;
  district?: string | null;
  address?: string | null;
  phone?: string | null;
  latitude: number;
  longitude: number;
  emergencyService: boolean;
  active: boolean;
}

export interface FacilityPayload {
  name: string;
  type: FacilityType;
  cityId: number;
  districtId: number | null;
  address: string | null;
  phone: string | null;
  latitude: number;
  longitude: number;
  emergencyService: boolean;
}

export type PaymentStatus = 'PENDING' | 'FAILED' | 'CAPTURED' | 'RELEASED' | 'REFUNDED';
export type PaymentMethod = 'MOBILE_MONEY' | 'CARD' | 'BANK_TRANSFER' | 'WALLET';
export type PayableType = 'APPOINTMENT' | 'PHARMACY_ORDER' | 'SUBSCRIPTION' | 'WALLET_TOPUP';

export interface PaymentRow {
  id: string;
  payerId: string;
  payableType: PayableType;
  payableId: string;
  method: PaymentMethod;
  provider: string;
  status: PaymentStatus;
  amount: number;
  currency: string;
  commissionAmount: number;
  netAmount: number;
  failureReason?: string | null;
  createdAt: string;
}

export type WithdrawalStatus = 'REQUESTED' | 'PAID' | 'REJECTED';

export interface WithdrawalRow {
  id: string;
  userId: string;
  amount: number;
  currency: string;
  method: 'MOBILE_MONEY' | 'BANK_TRANSFER';
  destination: string;
  status: WithdrawalStatus;
  rejectReason?: string | null;
  createdAt: string;
  processedAt?: string | null;
}

export interface FinanceSummary {
  volume: number;
  commissions: number;
  subscriptionRevenue: number;
  refunded: number;
  escrow: number;
  pendingPayments: number;
  pendingWithdrawals: number;
  pendingWithdrawalsAmount: number;
  platformBalance: number;
  currency: string;
}

export type SubscriptionStatus = 'TRIAL' | 'ACTIVE' | 'EXPIRED' | 'CANCELLED';

export interface SubscriptionRow {
  id: string;
  userId: string;
  userName?: string | null;
  planCode: string;
  planName: string;
  status: SubscriptionStatus;
  startsAt: string;
  endsAt: string;
}

export interface PlanRow {
  id: string;
  code: string;
  audience: 'PROFESSIONAL' | 'PATIENT';
  name: string;
  description?: string | null;
  price: number;
  currency: string;
  durationDays: number;
  commissionRate?: number | null;
  active: boolean;
}

export interface PlanPayload {
  name: string;
  description: string | null;
  price: number;
  durationDays: number;
  commissionRate: number | null;
  active: boolean;
}

// ---- Phase 7 : réclamations, avis, partenaires ----

export type ComplaintStatus = 'OPEN' | 'IN_REVIEW' | 'RESOLVED' | 'REJECTED';

export interface ComplaintRow {
  id: string;
  number: string;
  authorId: string;
  authorName: string | null;
  authorRole: 'PATIENT' | 'PROFESSIONAL';
  appointmentId: string | null;
  subject: string;
  status: ComplaintStatus;
  assignedTo: string | null;
  createdAt: string;
  resolvedAt: string | null;
}

export interface ComplaintMessage {
  id: string;
  authorId: string;
  fromAdmin: boolean;
  body: string;
  createdAt: string;
}

export interface ComplaintDetail {
  summary: ComplaintRow;
  description: string;
  resolution: string | null;
  messages: ComplaintMessage[];
}

export type ComplaintSummary = Record<ComplaintStatus, number>;

export type ReviewStatus = 'VISIBLE' | 'HIDDEN';

export interface ReviewRow {
  id: string;
  appointmentId: string;
  professionalId: string;
  authorName: string;
  rating: number;
  comment: string | null;
  status: ReviewStatus;
  hiddenReason: string | null;
  createdAt: string;
}

export type PartnerType =
  | 'CLINIC' | 'HEALTH_CENTER' | 'HOSPITAL' | 'PHARMACY' | 'LAB' | 'AMBULANCE' | 'INSURANCE' | 'MUTUAL';
export type PartnerStatus = 'ACTIVE' | 'INACTIVE';

export interface PartnerRow {
  id: string;
  name: string;
  type: PartnerType;
  cityId: number | null;
  cityName: string | null;
  address: string | null;
  contactEmail: string | null;
  contactPhone: string | null;
  contractStartDate: string | null;
  status: PartnerStatus;
}

export interface PartnerPayload {
  name: string;
  type: PartnerType;
  cityId: number | null;
  address: string | null;
  contactEmail: string | null;
  contactPhone: string | null;
  contractStartDate: string | null;
}

/** Référentiels gérés par l'administration. */
export interface DistrictAdmin {
  id: number;
  name: string;
  active: boolean;
}

export interface CityAdmin {
  id: number;
  name: string;
  countryCode: string;
  active: boolean;
  districts: DistrictAdmin[];
}

export interface SpecialtyAdmin {
  id: number;
  code: string;
  name: string;
  active: boolean;
}

export interface ProfessionalTypeAdmin {
  id: number;
  code: string;
  label: string;
}
