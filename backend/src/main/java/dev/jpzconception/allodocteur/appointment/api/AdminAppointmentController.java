package dev.jpzconception.allodocteur.appointment.api;

import dev.jpzconception.allodocteur.appointment.api.AppointmentDtos.AppointmentDto;
import dev.jpzconception.allodocteur.appointment.application.AppointmentService;
import dev.jpzconception.allodocteur.appointment.domain.AppointmentStatus;
import dev.jpzconception.allodocteur.appointment.domain.AppointmentType;
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
@RequestMapping("/api/v1/admin/appointments")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin – rendez-vous")
public class AdminAppointmentController {

    private final AppointmentService service;

    public AdminAppointmentController(AppointmentService service) {
        this.service = service;
    }

    /** Vue de suivi : les données de santé (détail du motif, date de naissance) ne sont pas exposées. */
    @GetMapping
    public PageResponse<AppointmentDto> list(
            @RequestParam(required = false) List<AppointmentStatus> status,
            @RequestParam(required = false) AppointmentType type,
            @RequestParam(required = false) UUID patientId,
            @RequestParam(required = false) UUID professionalId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String sort) {
        return PageResponse.of(service.listAll(patientId, professionalId, status, type, from, to, page, size, sort));
    }
}
