package dev.jpzconception.allodocteur.user.application;

import dev.jpzconception.allodocteur.admin.application.AuditService;
import dev.jpzconception.allodocteur.shared.error.ApiException;
import dev.jpzconception.allodocteur.user.domain.Role;
import dev.jpzconception.allodocteur.user.domain.User;
import dev.jpzconception.allodocteur.user.domain.UserStatus;
import dev.jpzconception.allodocteur.user.infrastructure.UserRepository;
import dev.jpzconception.allodocteur.user.infrastructure.UserSpecifications;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class UserService {

    /** Champs triables exposés à l'API (liste blanche) -> propriété JPA. */
    private static final Map<String, String> SORTABLE = Map.of(
            "createdAt", "createdAt",
            "email", "email",
            "lastName", "lastName",
            "status", "status");
    private static final Set<String> ALLOWED_TARGET_STATUS = Set.of("ACTIVE", "SUSPENDED");

    private final UserRepository users;
    private final AuditService audit;

    public UserService(UserRepository users, AuditService audit) {
        this.users = users;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public Page<User> search(String q, Role role, UserStatus status, int page, int size, String sort) {
        return users.findAll(UserSpecifications.filter(q, role, status), pageable(page, size, sort));
    }

    @Transactional(readOnly = true)
    public User get(UUID id) {
        return users.findById(id).orElseThrow(() -> ApiException.notFound("USER_NOT_FOUND", "Utilisateur introuvable."));
    }

    public User changeStatus(UUID actorId, UUID targetId, UserStatus newStatus) {
        if (!ALLOWED_TARGET_STATUS.contains(newStatus.name())) {
            throw ApiException.badRequest("INVALID_STATUS", "Statut cible non autorisé.");
        }
        if (actorId.equals(targetId)) {
            throw ApiException.badRequest("SELF_STATUS_CHANGE", "Vous ne pouvez pas modifier votre propre statut.");
        }
        User user = get(targetId);
        UserStatus previous = user.getStatus();
        user.setStatus(newStatus);
        audit.record(actorId, "USER_STATUS_CHANGED", "USER", targetId.toString(), previous + " -> " + newStatus);
        return user;
    }

    static Pageable pageable(int page, int size, String sort) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 100);
        Sort order = Sort.by(Sort.Direction.DESC, "createdAt");
        if (sort != null && !sort.isBlank()) {
            String[] parts = sort.split(",");
            String property = SORTABLE.get(parts[0].trim());
            if (property == null) {
                throw ApiException.badRequest("INVALID_SORT", "Tri non supporté : " + parts[0]);
            }
            Sort.Direction dir = parts.length > 1 && parts[1].trim().equalsIgnoreCase("asc")
                    ? Sort.Direction.ASC : Sort.Direction.DESC;
            order = Sort.by(dir, property);
        }
        return PageRequest.of(safePage, safeSize, order);
    }
}
