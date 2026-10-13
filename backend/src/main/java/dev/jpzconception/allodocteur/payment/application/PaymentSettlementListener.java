package dev.jpzconception.allodocteur.payment.application;

import dev.jpzconception.allodocteur.appointment.domain.AppointmentEvents;
import dev.jpzconception.allodocteur.payment.domain.PayableType;
import dev.jpzconception.allodocteur.pharmacy.application.PharmacyEvents;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Transforme les événements de prestation en mouvements de fonds, dans la transaction de l'émetteur : si le
 * remboursement échoue, l'annulation échoue aussi (les fonds ne se retrouvent jamais dans un état incohérent).
 */
@Component
public class PaymentSettlementListener {

    private final PaymentService payments;

    public PaymentSettlementListener(PaymentService payments) {
        this.payments = payments;
    }

    @EventListener
    public void onCompleted(AppointmentEvents.Completed e) {
        payments.releaseFor(PayableType.APPOINTMENT, e.appointmentId());
    }

    /** Le patient absent a bloqué le créneau : le professionnel est rémunéré. */
    @EventListener
    public void onNoShow(AppointmentEvents.NoShow e) {
        payments.releaseFor(PayableType.APPOINTMENT, e.appointmentId());
    }

    @EventListener
    public void onCancelled(AppointmentEvents.Cancelled e) {
        payments.refundFor(PayableType.APPOINTMENT, e.appointmentId());
    }

    @EventListener
    public void onOrder(PharmacyEvents.OrderStatusChanged e) {
        switch (e.status()) {
            case COMPLETED, DELIVERED -> payments.releaseFor(PayableType.PHARMACY_ORDER, e.orderId());
            case REJECTED, CANCELLED, DELIVERY_FAILED -> payments.refundFor(PayableType.PHARMACY_ORDER, e.orderId());
            default -> { }
        }
    }
}
