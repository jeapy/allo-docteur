package dev.jpzconception.allodocteur.teleconsultation.infrastructure;

import dev.jpzconception.allodocteur.teleconsultation.domain.SharedDocument;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SharedDocumentRepository extends JpaRepository<SharedDocument, UUID> {
    List<SharedDocument> findByAppointmentIdOrderByUploadedAtAsc(UUID appointmentId);
    long countByAppointmentId(UUID appointmentId);
}
