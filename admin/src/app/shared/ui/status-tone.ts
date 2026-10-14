import { BadgeTone } from './badge';

const TONES: Record<string, BadgeTone> = {
  // utilisateurs, partenaires, abonnements, avis
  ACTIVE: 'success', VISIBLE: 'success', APPROVED: 'success', COMPLETED: 'success', RESOLVED: 'success',
  CAPTURED: 'success', RELEASED: 'success', PAID: 'success', GRANTED: 'success',
  PENDING: 'warning', REQUESTED: 'warning', TRIAL: 'warning', IN_REVIEW: 'warning', OPEN: 'warning',
  CONFIRMED: 'info', IN_PROGRESS: 'info', ACCEPTED: 'info', REFUNDED: 'info',
  SUSPENDED: 'danger', REJECTED: 'danger', FAILED: 'danger', NO_SHOW: 'danger', DENIED: 'danger',
  CANCELLED: 'neutral', EXPIRED: 'neutral', INACTIVE: 'neutral', HIDDEN: 'neutral',
};

/** Couleur de pastille d'un statut métier ; neutre si le statut est inconnu. */
export function statusTone(status: string | null | undefined): BadgeTone {
  return (status && TONES[status]) || 'neutral';
}
