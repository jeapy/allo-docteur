package dev.jpzconception.allodocteur.partner.infrastructure;

import dev.jpzconception.allodocteur.partner.domain.Partner;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface PartnerRepository extends JpaRepository<Partner, UUID>, JpaSpecificationExecutor<Partner> {}
