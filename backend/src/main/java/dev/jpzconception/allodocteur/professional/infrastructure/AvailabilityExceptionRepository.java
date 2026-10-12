package dev.jpzconception.allodocteur.professional.infrastructure;

import dev.jpzconception.allodocteur.professional.domain.AvailabilityException;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AvailabilityExceptionRepository extends JpaRepository<AvailabilityException, UUID> {
    List<AvailabilityException> findByProfessionalIdAndDateGreaterThanEqualOrderByDateAscStartTimeAsc(
            UUID professionalId, LocalDate from);
}
