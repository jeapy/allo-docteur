package dev.jpzconception.allodocteur.notification.application;

import dev.jpzconception.allodocteur.appointment.domain.AppointmentEvents;
import dev.jpzconception.allodocteur.notification.domain.NotificationType;
import dev.jpzconception.allodocteur.payment.application.PaymentEvents;
import dev.jpzconception.allodocteur.pharmacy.application.PharmacyEvents;
import dev.jpzconception.allodocteur.pharmacy.application.PharmacyOrderAccess;
import dev.jpzconception.allodocteur.pharmacy.domain.OrderStatus;
import dev.jpzconception.allodocteur.prescription.application.PrescriptionEvents;
import dev.jpzconception.allodocteur.professional.application.ProfessionalEvents;
import dev.jpzconception.allodocteur.professional.application.ProfessionalLookup;
import dev.jpzconception.allodocteur.review.application.ReviewEvents;
import dev.jpzconception.allodocteur.complaint.application.ComplaintEvents;
import dev.jpzconception.allodocteur.sos.application.SosEvents;
import dev.jpzconception.allodocteur.subscription.application.SubscriptionEvents;
import java.util.UUID;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Traduit les événements métier en notifications. Les textes ne contiennent jamais de donnée de santé (motif, diagnostic,
 * contenu d'ordonnance) : une notification peut être lue sur un écran verrouillé ou partir par SMS.
 * Les écouteurs sont synchrones : ils s'exécutent dans la transaction de l'émetteur, donc rien n'est notifié si elle échoue.
 */
@Component
public class NotificationListener {

    private final NotificationService notifications;
    private final ProfessionalLookup professionals;
    private final PharmacyOrderAccess pharmacyOrders;

    public NotificationListener(NotificationService notifications, ProfessionalLookup professionals,
                                PharmacyOrderAccess pharmacyOrders) {
        this.notifications = notifications;
        this.professionals = professionals;
        this.pharmacyOrders = pharmacyOrders;
    }

    // ---- Rendez-vous ----

    @EventListener
    public void on(AppointmentEvents.Requested e) {
        notifications.notify(e.professionalId(), NotificationType.APPOINTMENT, "Nouvelle demande de rendez-vous",
                "Un patient demande un rendez-vous. Consultez-le pour le confirmer ou le refuser.", "APPOINTMENT", e.appointmentId());
    }

    @EventListener
    public void on(AppointmentEvents.Confirmed e) {
        notifications.notify(e.patientId(), NotificationType.APPOINTMENT, "Rendez-vous confirmé",
                "Votre rendez-vous a été confirmé.", "APPOINTMENT", e.appointmentId());
    }

    @EventListener
    public void on(AppointmentEvents.Cancelled e) {
        UUID other = e.cancelledBy() != null && e.cancelledBy().equals(e.patientId()) ? e.professionalId() : e.patientId();
        notifications.notify(other, NotificationType.APPOINTMENT, "Rendez-vous annulé",
                "Un rendez-vous a été annulé.", "APPOINTMENT", e.appointmentId());
    }

    @EventListener
    public void on(AppointmentEvents.Started e) {
        notifications.notify(e.patientId(), NotificationType.APPOINTMENT, "Votre consultation commence",
                "Le professionnel a démarré la consultation.", "APPOINTMENT", e.appointmentId());
    }

    @EventListener
    public void on(AppointmentEvents.Completed e) {
        notifications.notify(e.patientId(), NotificationType.REVIEW, "Donnez votre avis",
                "Votre consultation est terminée. Vous pouvez maintenant noter le professionnel.", "APPOINTMENT", e.appointmentId());
    }

    @EventListener
    public void on(AppointmentEvents.NoShow e) {
        notifications.notify(e.patientId(), NotificationType.APPOINTMENT, "Rendez-vous manqué",
                "Votre rendez-vous a été marqué comme manqué.", "APPOINTMENT", e.appointmentId());
    }

    // ---- Paiements ----

    @EventListener
    public void on(PaymentEvents.Captured e) {
        if (e.payableType() == dev.jpzconception.allodocteur.payment.domain.PayableType.WALLET_TOPUP) {
            return;
        }
        notifications.notify(e.payerId(), NotificationType.PAYMENT, "Paiement reçu",
                "Votre paiement de " + amount(e.amount()) + " a bien été enregistré.", "PAYMENT", e.paymentId());
    }

    @EventListener
    public void on(PaymentEvents.Released e) {
        if (e.payeeUserId() != null) {
            notifications.notify(e.payeeUserId(), NotificationType.PAYMENT, "Gains crédités",
                    "Vos gains ont été crédités sur votre portefeuille.", "PAYMENT", e.paymentId());
        }
    }

    @EventListener
    public void on(PaymentEvents.Refunded e) {
        notifications.notify(e.payerId(), NotificationType.PAYMENT, "Paiement remboursé",
                "Un remboursement de " + amount(e.amount()) + " a été effectué.", "PAYMENT", e.paymentId());
    }

    @EventListener
    public void on(PaymentEvents.Failed e) {
        notifications.notify(e.payerId(), NotificationType.PAYMENT, "Paiement échoué",
                "Votre paiement n'a pas abouti. Vous pouvez réessayer.", "PAYMENT", e.paymentId());
    }

    // ---- Abonnements ----

    @EventListener
    public void on(SubscriptionEvents.Activated e) {
        notifications.notify(e.userId(), NotificationType.SUBSCRIPTION, "Abonnement activé",
                "Votre abonnement est actif.", "SUBSCRIPTION", e.subscriptionId());
    }

    // ---- SOS ----

    @EventListener
    public void on(SosEvents.Created e) {
        for (UUID proId : professionals.homeVisitProfessionalIds(e.cityId())) {
            notifications.notify(proId, NotificationType.SOS, "Alerte SOS près de vous",
                    "Une alerte d'urgence attend un professionnel disponible.", "SOS", e.alertId());
        }
    }

    @EventListener
    public void on(SosEvents.Accepted e) {
        notifications.notify(e.accountId(), NotificationType.SOS, "Un professionnel arrive",
                "Votre alerte SOS a été prise en charge.", "SOS", e.alertId());
    }

    @EventListener
    public void on(SosEvents.Resolved e) {
        notifications.notify(e.accountId(), NotificationType.SOS, "Alerte SOS clôturée",
                "Votre alerte SOS a été clôturée.", "SOS", e.alertId());
    }

    // ---- Pharmacie ----

    @EventListener
    public void on(PharmacyEvents.OrderStatusChanged e) {
        if (e.status() == OrderStatus.SENT) {
            for (UUID userId : pharmacyOrders.memberUserIds(e.pharmacyId())) {
                notifications.notify(userId, NotificationType.PHARMACY, "Nouvelle commande",
                        "Une nouvelle commande est arrivée.", "PHARMACY_ORDER", e.orderId());
            }
            return;
        }
        String text = switch (e.status()) {
            case ACCEPTED -> "Votre commande a été acceptée par la pharmacie.";
            case READY -> "Votre commande est prête.";
            case OUT_FOR_DELIVERY -> "Votre commande est en cours de livraison.";
            case COMPLETED, DELIVERED -> "Votre commande est terminée.";
            case DELIVERY_FAILED -> "La livraison de votre commande a échoué.";
            case REJECTED -> "Votre commande a été refusée par la pharmacie.";
            case CANCELLED -> "Votre commande a été annulée.";
            default -> null;
        };
        if (text != null) {
            notifications.notify(e.accountId(), NotificationType.PHARMACY, "Commande pharmacie", text, "PHARMACY_ORDER", e.orderId());
        }
    }

    // ---- Ordonnances, avis, réclamations, profil ----

    @EventListener
    public void on(PrescriptionEvents.Issued e) {
        notifications.notify(e.accountId(), NotificationType.PRESCRIPTION, "Nouvelle ordonnance",
                "Une ordonnance est disponible dans votre dossier.", "PRESCRIPTION", e.prescriptionId());
    }

    @EventListener
    public void on(ReviewEvents.Posted e) {
        notifications.notify(e.professionalId(), NotificationType.REVIEW, "Nouvel avis",
                "Un patient a laissé un avis (" + e.rating() + "/5).", "REVIEW", e.reviewId());
    }

    @EventListener
    public void on(ComplaintEvents.Updated e) {
        String text = e.reply()
                ? "L'équipe a répondu à votre réclamation " + e.number() + "."
                : switch (e.status()) {
                    case IN_REVIEW -> "Votre réclamation " + e.number() + " est en cours d'examen.";
                    case RESOLVED -> "Votre réclamation " + e.number() + " a été résolue.";
                    case REJECTED -> "Votre réclamation " + e.number() + " a été clôturée sans suite.";
                    default -> "Votre réclamation " + e.number() + " a été mise à jour.";
                };
        notifications.notify(e.authorId(), NotificationType.COMPLAINT_UPDATE, "Réclamation " + e.number(), text,
                "COMPLAINT", e.complaintId());
    }

    @EventListener
    public void on(ProfessionalEvents.VerificationChanged e) {
        String text = switch (e.status()) {
            case APPROVED -> "Votre profil a été validé : vous êtes visible dans l'annuaire.";
            case REJECTED -> "Votre profil a été refusé. Consultez le motif et corrigez votre dossier.";
            case SUSPENDED -> "Votre profil a été suspendu.";
            default -> null;
        };
        if (text != null) {
            notifications.notify(e.professionalId(), NotificationType.PROFESSIONAL, "Votre profil professionnel", text,
                    "PROFESSIONAL", e.professionalId());
        }
    }

    private static String amount(java.math.BigDecimal a) {
        return a.stripTrailingZeros().toPlainString() + " XOF";
    }
}
