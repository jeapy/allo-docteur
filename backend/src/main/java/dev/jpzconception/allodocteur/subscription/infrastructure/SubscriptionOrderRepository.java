package dev.jpzconception.allodocteur.subscription.infrastructure;

import dev.jpzconception.allodocteur.subscription.domain.SubscriptionOrder;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SubscriptionOrderRepository extends JpaRepository<SubscriptionOrder, UUID> {
}
