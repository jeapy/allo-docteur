package dev.jpzconception.allodocteur.teleconsultation.application;

import dev.jpzconception.allodocteur.appointment.application.AppointmentAccess.Ref;
import dev.jpzconception.allodocteur.appointment.domain.AppointmentStatus;
import dev.jpzconception.allodocteur.shared.error.ApiException;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.springframework.stereotype.Component;

/** Quand peut-on écrire ou partager un fichier ? Lire reste toujours possible pour les deux participants. */
@Component
public class ConversationPolicy {

    private final MessagingProperties props;
    private final Clock clock;

    public ConversationPolicy(MessagingProperties props, Clock clock) {
        this.props = props;
        this.clock = clock;
    }

    public void requireOpen(Ref appointment) {
        if (!isOpen(appointment)) {
            throw ApiException.conflict("CONVERSATION_CLOSED", "La conversation de ce rendez-vous est fermée.");
        }
    }

    boolean isOpen(Ref a) {
        AppointmentStatus s = a.status();
        if (s == AppointmentStatus.CONFIRMED || s == AppointmentStatus.IN_PROGRESS) return true;
        if (s == AppointmentStatus.COMPLETED && a.completedAt() != null) {
            Instant limit = a.completedAt().plus(props.postCompletionDays(), ChronoUnit.DAYS);
            return Instant.now(clock).isBefore(limit);
        }
        return false;
    }
}
