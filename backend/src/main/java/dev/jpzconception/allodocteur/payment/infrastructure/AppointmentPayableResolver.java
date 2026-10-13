package dev.jpzconception.allodocteur.payment.infrastructure;

import dev.jpzconception.allodocteur.appointment.application.AppointmentAccess;
import dev.jpzconception.allodocteur.appointment.application.AppointmentAccess.Ref;
import dev.jpzconception.allodocteur.appointment.domain.AppointmentStatus;
import dev.jpzconception.allodocteur.payment.application.PayableResolver;
import dev.jpzconception.allodocteur.payment.domain.PayableType;
import dev.jpzconception.allodocteur.payment.domain.PayeeType;
import dev.jpzconception.allodocteur.shared.error.ApiException;
import dev.jpzconception.allodocteur.shared.security.AuthenticatedUser;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Un rendez-vous se paie par son patient (titulaire du compte) ; les fonds vont au professionnel, net de commission. */
@Component
@Transactional(readOnly = true)
public class AppointmentPayableResolver implements PayableResolver {

    private static final Set<AppointmentStatus> PAYABLE = EnumSet.of(AppointmentStatus.REQUESTED,
            AppointmentStatus.CONFIRMED, AppointmentStatus.IN_PROGRESS, AppointmentStatus.COMPLETED);

    private final AppointmentAccess appointments;

    public AppointmentPayableResolver(AppointmentAccess appointments) {
        this.appointments = appointments;
    }

    @Override
    public PayableType type() {
        return PayableType.APPOINTMENT;
    }

    @Override
    public boolean escrow() {
        return true;
    }

    @Override
    public PayableInfo resolve(AuthenticatedUser payer, UUID payableId) {
        Ref a = appointments.requireParticipant(payer, payableId);
        if (!a.isPatient(payer.id())) {
            throw ApiException.notFound("APPOINTMENT_NOT_FOUND", "Rendez-vous introuvable.");
        }
        if (!PAYABLE.contains(a.status())) {
            throw ApiException.conflict("NOT_PAYABLE", "Ce rendez-vous ne peut plus être payé (statut " + a.status() + ").");
        }
        if (a.fee() == null || a.fee().signum() <= 0) {
            throw ApiException.conflict("NOTHING_TO_PAY", "Ce rendez-vous n'est pas payant.");
        }
        return new PayableInfo(a.fee(), "XOF", PayeeType.USER, a.professionalId(), "Rendez-vous " + a.id());
    }

    @Override
    public State state(UUID payableId) {
        return appointments.findRef(payableId).map(a -> switch (a.status()) {
            case COMPLETED, NO_SHOW -> State.DELIVERED;
            case CANCELLED -> State.CLOSED;
            default -> State.OPEN;
        }).orElse(State.CLOSED);
    }
}
