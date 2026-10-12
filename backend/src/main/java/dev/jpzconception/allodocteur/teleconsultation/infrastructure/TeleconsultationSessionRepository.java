package dev.jpzconception.allodocteur.teleconsultation.infrastructure;

import dev.jpzconception.allodocteur.teleconsultation.domain.TeleconsultationSession;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TeleconsultationSessionRepository extends JpaRepository<TeleconsultationSession, UUID> {
    Optional<TeleconsultationSession> findByAppointmentId(UUID appointmentId);
}
