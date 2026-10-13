package dev.jpzconception.allodocteur.medicalrecord.infrastructure;

import dev.jpzconception.allodocteur.medicalrecord.domain.ConsentVersion;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConsentVersionRepository extends JpaRepository<ConsentVersion, String> {
    Optional<ConsentVersion> findFirstByPublishedAtLessThanEqualOrderByPublishedAtDesc(Instant now);
}
