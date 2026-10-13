package dev.jpzconception.allodocteur.payment.infrastructure;

import dev.jpzconception.allodocteur.payment.domain.Withdrawal;
import dev.jpzconception.allodocteur.payment.domain.WithdrawalStatus;
import java.math.BigDecimal;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface WithdrawalRepository extends JpaRepository<Withdrawal, UUID> {

    Page<Withdrawal> findByUserId(UUID userId, Pageable pageable);

    Page<Withdrawal> findByStatus(WithdrawalStatus status, Pageable pageable);

    long countByStatus(WithdrawalStatus status);

    @Query("""
            select coalesce(sum(w.amount), 0) from Withdrawal w
            where w.status = dev.jpzconception.allodocteur.payment.domain.WithdrawalStatus.REQUESTED""")
    BigDecimal requestedTotal();
}
