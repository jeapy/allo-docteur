package dev.jpzconception.allodocteur.admin.api;

import dev.jpzconception.allodocteur.admin.application.AuditQueryService;
import dev.jpzconception.allodocteur.admin.application.AuditQueryService.AuditEntry;
import dev.jpzconception.allodocteur.shared.web.PageResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/audit-logs")
@PreAuthorize("hasRole('ADMIN') and hasAuthority('LEVEL_SUPER')")
@Tag(name = "Admin – journal d'audit")
public class AdminAuditController {

    private final AuditQueryService service;

    public AdminAuditController(AuditQueryService service) {
        this.service = service;
    }

    @GetMapping
    public PageResponse<AuditEntry> list(
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String targetType,
            @RequestParam(required = false) String targetId,
            @RequestParam(required = false) UUID actorId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return PageResponse.of(service.search(action, targetType, targetId, actorId, from, to, page, size));
    }

    @GetMapping("/actions")
    public List<String> actions() {
        return service.actions();
    }
}
