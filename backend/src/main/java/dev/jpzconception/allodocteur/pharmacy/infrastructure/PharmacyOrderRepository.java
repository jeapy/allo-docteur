package dev.jpzconception.allodocteur.pharmacy.infrastructure;

import dev.jpzconception.allodocteur.pharmacy.domain.OrderStatus;
import dev.jpzconception.allodocteur.pharmacy.domain.PharmacyOrder;
import java.util.Collection;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface PharmacyOrderRepository extends JpaRepository<PharmacyOrder, UUID> {

    Page<PharmacyOrder> findByAccountId(UUID accountId, Pageable pageable);

    Page<PharmacyOrder> findByPharmacyIdIn(Collection<UUID> pharmacyIds, Pageable pageable);

    Page<PharmacyOrder> findByPharmacyIdInAndStatusIn(Collection<UUID> pharmacyIds, Collection<OrderStatus> statuses,
                                                      Pageable pageable);

    @Query(value = "select nextval('pharmacy_order_number_seq')", nativeQuery = true)
    long nextNumber();
}
