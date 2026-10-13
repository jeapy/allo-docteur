package dev.jpzconception.allodocteur.medicalrecord.infrastructure;

import dev.jpzconception.allodocteur.medicalrecord.domain.Consent;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConsentRepository extends JpaRepository<Consent, UUID> {
    Optional<Consent> findByUserIdAndRevokedAtIsNull(UUID userId);

    List<Consent> findByUserIdOrderByGrantedAtDesc(UUID userId);
}
