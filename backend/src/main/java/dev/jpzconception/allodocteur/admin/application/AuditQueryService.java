package dev.jpzconception.allodocteur.admin.application;

import dev.jpzconception.allodocteur.admin.domain.AuditLog;
import dev.jpzconception.allodocteur.admin.infrastructure.AuditLogRepository;
import dev.jpzconception.allodocteur.admin.infrastructure.AuditLogSpecifications;
import dev.jpzconception.allodocteur.user.domain.User;
import dev.jpzconception.allodocteur.user.infrastructure.UserRepository;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Consultation du journal d'audit (lecture seule : le journal est en ajout seul). */
@Service
@Transactional(readOnly = true)
public class AuditQueryService {

    public record AuditEntry(UUID id, UUID actorId, String actorEmail, String action, String targetType,
                             String targetId, String ipAddress, String details, Instant createdAt) {}

    private final AuditLogRepository logs;
    private final UserRepository users;

    public AuditQueryService(AuditLogRepository logs, UserRepository users) {
        this.logs = logs;
        this.users = users;
    }

    public Page<AuditEntry> search(String action, String targetType, String targetId, UUID actorId, Instant from,
                                   Instant to, int page, int size) {
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100),
                Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by("id")));
        Page<AuditLog> found = logs.findAll(
                AuditLogSpecifications.filter(action, targetType, targetId, actorId, from, to), pageable);
        Map<UUID, String> emails = users.findAllById(found.getContent().stream().map(AuditLog::getActorId)
                        .filter(Objects::nonNull).collect(Collectors.toSet())).stream()
                .collect(Collectors.toMap(User::getId, User::getEmail, (a, b) -> a));
        List<AuditEntry> content = found.getContent().stream()
                .map(a -> new AuditEntry(a.getId(), a.getActorId(), a.getActorId() == null ? null : emails.get(a.getActorId()),
                        a.getAction(), a.getTargetType(), a.getTargetId(), a.getIpAddress(), a.getDetails(),
                        a.getCreatedAt()))
                .toList();
        return new PageImpl<>(content, pageable, found.getTotalElements());
    }

    /** Actions déjà enregistrées, pour alimenter le filtre de l'écran. */
    public List<String> actions() {
        return logs.findDistinctActions();
    }
}
