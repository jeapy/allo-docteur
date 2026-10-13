package dev.jpzconception.allodocteur.appointment.application;

import dev.jpzconception.allodocteur.appointment.domain.AppointmentStatus;
import dev.jpzconception.allodocteur.appointment.domain.AppointmentType;
import dev.jpzconception.allodocteur.appointment.infrastructure.AppointmentRepository;
import dev.jpzconception.allodocteur.shared.error.ApiException;
import dev.jpzconception.allodocteur.shared.security.AuthenticatedUser;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
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

    public record Ref(UUID id, UUID patientId, UUID dependentId, String beneficiaryName, LocalDate beneficiaryBirthDate,
                      UUID professionalId, AppointmentType type, AppointmentStatus status,
                      Instant startAt, Instant endAt, Instant completedAt, BigDecimal fee) {
        public boolean isPatient(UUID userId) {
            return patientId.equals(userId);
        }
    }

    private final AppointmentRepository appointments;

    public AppointmentAccess(AppointmentRepository appointments) {
        this.appointments = appointments;
    }

    /**
     * Le professionnel a-t-il un lien de soin avec cette personne ? Oui s'il a au moins un rendez-vous confirmé, en cours
     * ou terminé avec elle. {@code dependentId} nul = le titulaire du compte lui-même.
     */
    public boolean hasCareRelationship(UUID professionalId, UUID accountId, UUID dependentId) {
        return dependentId == null
                ? appointments.existsCareForSelf(professionalId, accountId, AppointmentStatus.CARE)
                : appointments.existsCareForDependent(professionalId, dependentId, AppointmentStatus.CARE);
    }

    /** 404 (et non 403) pour un non-participant : on ne révèle pas l'existence du rendez-vous. */
    public Ref requireParticipant(AuthenticatedUser user, UUID appointmentId) {
        return appointments.findById(appointmentId)
                .filter(a -> a.isParticipant(user.id()))
                .map(AppointmentAccess::toRef)
                .orElseThrow(() -> ApiException.notFound("APPOINTMENT_NOT_FOUND", "Rendez-vous introuvable."));
    }

    /** Lecture sans contrôle d'accès, réservée aux traitements internes (paiement). */
    public Optional<Ref> findRef(UUID appointmentId) {
        return appointments.findById(appointmentId).map(AppointmentAccess::toRef);
    }

    private static Ref toRef(dev.jpzconception.allodocteur.appointment.domain.Appointment a) {
        return new Ref(a.getId(), a.getPatientId(), a.getDependentId(), a.getBeneficiaryName(),
                a.getBeneficiaryBirthDate(), a.getProfessionalId(), a.getType(), a.getStatus(),
                a.getStartAt(), a.getEndAt(), a.getCompletedAt(), a.getFee());
    }
}
