package dev.jpzconception.allodocteur.medicalrecord.api;

import dev.jpzconception.allodocteur.medicalrecord.api.MedicalRecordDtos.AccessLogDto;
import dev.jpzconception.allodocteur.medicalrecord.application.MedicalAccessLogQuery;
import dev.jpzconception.allodocteur.medicalrecord.application.MedicalRecordService;
import dev.jpzconception.allodocteur.medicalrecord.domain.AccessOutcome;
import dev.jpzconception.allodocteur.shared.web.PageResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Instant;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/medical-access-logs")
@PreAuthorize("hasAuthority('LEVEL_SUPER')")
@Tag(name = "Admin – journal d'accès aux dossiers")
public class AdminAccessLogController {

    private final MedicalRecordService service;

    public AdminAccessLogController(MedicalRecordService service) {
        this.service = service;
    }

    /** Métadonnées d'accès uniquement : aucun contenu médical. Réservé au niveau SUPER (conformité). */
    @GetMapping
    public PageResponse<AccessLogDto> list(
            @RequestParam(required = false) UUID recordId,
            @RequestParam(required = false) UUID actorId,
            @RequestParam(required = false) AccessOutcome outcome,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return PageResponse.of(service.searchAccessLogs(new MedicalAccessLogQuery(recordId, actorId, outcome, from, to), page, size));
    }
}
