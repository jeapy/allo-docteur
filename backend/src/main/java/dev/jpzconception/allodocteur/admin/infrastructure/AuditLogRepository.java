package dev.jpzconception.allodocteur.admin.infrastructure;

import dev.jpzconception.allodocteur.admin.domain.AuditLog;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

public interface AuditLogRepository extends JpaRepository<AuditLog, UUID>, JpaSpecificationExecutor<AuditLog> {
    long countByAction(String action);

    @Query("select distinct a.action from AuditLog a order by a.action")
    List<String> findDistinctActions();
}
