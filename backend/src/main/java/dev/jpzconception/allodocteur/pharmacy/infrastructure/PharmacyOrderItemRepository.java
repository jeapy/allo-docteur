package dev.jpzconception.allodocteur.pharmacy.infrastructure;

import dev.jpzconception.allodocteur.pharmacy.domain.PharmacyOrderItem;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PharmacyOrderItemRepository extends JpaRepository<PharmacyOrderItem, UUID> {

    List<PharmacyOrderItem> findByOrderIdOrderByLineNumber(UUID orderId);
}
