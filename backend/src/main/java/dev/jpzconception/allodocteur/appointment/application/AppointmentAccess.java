package dev.jpzconception.allodocteur.appointment.application;

import dev.jpzconception.allodocteur.appointment.domain.AppointmentStatus;
import dev.jpzconception.allodocteur.appointment.domain.AppointmentType;
import dev.jpzconception.allodocteur.appointment.infrastructure.AppointmentRepository;
import dev.jpzconception.allodocteur.shared.error.ApiException;
import dev.jpzconception.allodocteur.shared.security.AuthenticatedUser;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Porte d'entrée des autres modules (messagerie, documents, téléconsultation, dossier médical…) pour vérifier qu'un
 * utilisateur est bien partie prenante d'un rendez-vous. Les administrateurs n'ont volontairement pas accès au contenu.
 */
@Service
@Transactional(readOnly = true)
public class AppointmentAccess {

    public record Ref(UUID id, UUID patientId, UUID professionalId, AppointmentType type, AppointmentStatus status,
                      Instant startAt, Instant endAt, Instant completedAt) {
        public boolean isPatient(UUID userId) {
            return patientId.equals(userId);
        }
    }

    private final AppointmentRepository appointments;

    public AppointmentAccess(AppointmentRepository appointments) {
        this.appointments = appointments;
    }

    /** 404 (et non 403) pour un non-participant : on ne révèle pas l'existence du rendez-vous. */
    public Ref requireParticipant(AuthenticatedUser user, UUID appointmentId) {
        return appointments.findById(appointmentId)
                .filter(a -> a.isParticipant(user.id()))
                .map(a -> new Ref(a.getId(), a.getPatientId(), a.getProfessionalId(), a.getType(), a.getStatus(),
                        a.getStartAt(), a.getEndAt(), a.getCompletedAt()))
                .orElseThrow(() -> ApiException.notFound("APPOINTMENT_NOT_FOUND", "Rendez-vous introuvable."));
    }
}
