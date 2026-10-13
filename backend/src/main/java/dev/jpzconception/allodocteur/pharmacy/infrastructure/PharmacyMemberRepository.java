package dev.jpzconception.allodocteur.pharmacy.infrastructure;

import dev.jpzconception.allodocteur.pharmacy.domain.PharmacyMember;
import dev.jpzconception.allodocteur.pharmacy.domain.PharmacyMemberId;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PharmacyMemberRepository extends JpaRepository<PharmacyMember, PharmacyMemberId> {

    List<PharmacyMember> findByUserId(UUID userId);

    List<PharmacyMember> findByPharmacyId(UUID pharmacyId);

    boolean existsByPharmacyIdAndUserId(UUID pharmacyId, UUID userId);
}
