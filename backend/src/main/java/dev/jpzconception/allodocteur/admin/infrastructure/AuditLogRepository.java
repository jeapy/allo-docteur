package dev.jpzconception.allodocteur.admin.infrastructure;

import dev.jpzconception.allodocteur.admin.domain.AuditLog;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {
    long countByAction(String action);
}
