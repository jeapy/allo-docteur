package dev.jpzconception.allodocteur.admin.application;

import dev.jpzconception.allodocteur.admin.domain.AuditLog;
import dev.jpzconception.allodocteur.admin.infrastructure.AuditLogRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditService {

    private final AuditLogRepository repository;
    private final Clock clock;

    public AuditService(AuditLogRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Transactional
    public void record(UUID actorId, String action, String targetType, String targetId, String details) {
        record(actorId, action, targetType, targetId, null, details);
    }

    @Transactional
    public void record(UUID actorId, String action, String targetType, String targetId, String ip, String details) {
        repository.save(new AuditLog(actorId, action, targetType, targetId, ip, details, Instant.now(clock)));
    }
}
