package dev.jpzconception.allodocteur.partner.infrastructure;

import dev.jpzconception.allodocteur.partner.domain.InsurancePolicy;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InsurancePolicyRepository extends JpaRepository<InsurancePolicy, UUID> {

    List<InsurancePolicy> findByAccountIdOrderByValidUntilDesc(UUID accountId);

    Optional<InsurancePolicy> findByIdAndAccountId(UUID id, UUID accountId);
}
