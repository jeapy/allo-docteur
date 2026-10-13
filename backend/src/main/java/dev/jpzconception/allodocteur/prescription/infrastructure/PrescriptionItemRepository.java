package dev.jpzconception.allodocteur.prescription.infrastructure;

import dev.jpzconception.allodocteur.prescription.domain.PrescriptionItem;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PrescriptionItemRepository extends JpaRepository<PrescriptionItem, UUID> {

    List<PrescriptionItem> findByPrescriptionIdOrderByLineNumber(UUID prescriptionId);

    List<PrescriptionItem> findByPrescriptionIdInOrderByLineNumber(Collection<UUID> prescriptionIds);
}
