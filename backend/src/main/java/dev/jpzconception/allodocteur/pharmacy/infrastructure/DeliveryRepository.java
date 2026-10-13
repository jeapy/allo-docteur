package dev.jpzconception.allodocteur.pharmacy.infrastructure;

import dev.jpzconception.allodocteur.pharmacy.domain.Delivery;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DeliveryRepository extends JpaRepository<Delivery, UUID> {

    Optional<Delivery> findByOrderId(UUID orderId);
}
