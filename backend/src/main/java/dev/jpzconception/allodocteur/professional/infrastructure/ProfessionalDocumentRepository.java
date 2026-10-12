package dev.jpzconception.allodocteur.professional.infrastructure;

import dev.jpzconception.allodocteur.professional.domain.ProfessionalDocument;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProfessionalDocumentRepository extends JpaRepository<ProfessionalDocument, UUID> {
    List<ProfessionalDocument> findByProfessionalIdOrderByUploadedAtDesc(UUID professionalId);
    long countByProfessionalId(UUID professionalId);
}
