package dev.jpzconception.allodocteur.medicalrecord.api;

import dev.jpzconception.allodocteur.medicalrecord.api.MedicalRecordDtos.AccessLogDto;
import dev.jpzconception.allodocteur.medicalrecord.api.MedicalRecordDtos.EntryDto;
import dev.jpzconception.allodocteur.medicalrecord.api.MedicalRecordDtos.EntryRequest;
import dev.jpzconception.allodocteur.medicalrecord.api.MedicalRecordDtos.RecordDto;
import dev.jpzconception.allodocteur.medicalrecord.api.MedicalRecordDtos.SummaryRequest;
import dev.jpzconception.allodocteur.medicalrecord.application.MedicalRecordService;
import dev.jpzconception.allodocteur.medicalrecord.domain.EntryKind;
import dev.jpzconception.allodocteur.shared.security.AuthenticatedUser;
import dev.jpzconception.allodocteur.shared.web.PageResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Dossier médical et carnet de santé. Réservé au patient (propriétaire) et à ses professionnels traitants. */
@RestController
@RequestMapping("/api/v1")
@PreAuthorize("hasAnyRole('PATIENT', 'PROFESSIONAL')")
@Tag(name = "Dossier médical")
public class MedicalRecordController {

    private final MedicalRecordService service;

    public MedicalRecordController(MedicalRecordService service) {
        this.service = service;
    }

    @GetMapping("/medical-records/me")
    @PreAuthorize("hasRole('PATIENT')")
    public RecordDto own(@AuthenticationPrincipal AuthenticatedUser me) {
        return service.own(me);
    }

    @GetMapping("/medical-records/dependents/{dependentId}")
    @PreAuthorize("hasRole('PATIENT')")
    public RecordDto dependent(@AuthenticationPrincipal AuthenticatedUser me, @PathVariable UUID dependentId) {
        return service.forDependent(me, dependentId);
    }

    /** Dossier de la personne concernée par le rendez-vous (professionnel du rendez-vous, avec consentement). */
    @GetMapping("/appointments/{appointmentId}/medical-record")
    @PreAuthorize("hasRole('PROFESSIONAL')")
    public RecordDto forAppointment(@AuthenticationPrincipal AuthenticatedUser me, @PathVariable UUID appointmentId) {
        return service.forAppointment(me, appointmentId);
    }

    @GetMapping("/medical-records/{id}")
    public RecordDto get(@AuthenticationPrincipal AuthenticatedUser me, @PathVariable UUID id) {
        return service.get(me, id);
    }

    @PutMapping("/medical-records/{id}/summary")
    @PreAuthorize("hasRole('PATIENT')")
    public RecordDto updateSummary(@AuthenticationPrincipal AuthenticatedUser me, @PathVariable UUID id,
                                   @Valid @RequestBody SummaryRequest request) {
        return service.updateSummary(me, id, request);
    }

    @GetMapping("/medical-records/{id}/entries")
    public PageResponse<EntryDto> entries(@AuthenticationPrincipal AuthenticatedUser me, @PathVariable UUID id,
                                          @RequestParam(required = false) EntryKind kind,
                                          @RequestParam(defaultValue = "0") int page,
                                          @RequestParam(defaultValue = "20") int size) {
        return PageResponse.of(service.listEntries(me, id, kind, page, size));
    }

    @PostMapping("/medical-records/{id}/entries")
    @ResponseStatus(HttpStatus.CREATED)
    public EntryDto addEntry(@AuthenticationPrincipal AuthenticatedUser me, @PathVariable UUID id,
                             @Valid @RequestBody EntryRequest request) {
        return service.addEntry(me, id, request);
    }

    @DeleteMapping("/medical-records/{id}/entries/{entryId}")
    @PreAuthorize("hasRole('PATIENT')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteEntry(@AuthenticationPrincipal AuthenticatedUser me, @PathVariable UUID id,
                            @PathVariable UUID entryId) {
        service.deleteEntry(me, id, entryId);
    }

    /** Qui a consulté ou modifié ce dossier, et quand (propriétaire seulement). */
    @GetMapping("/medical-records/{id}/access-log")
    @PreAuthorize("hasRole('PATIENT')")
    public PageResponse<AccessLogDto> accessLog(@AuthenticationPrincipal AuthenticatedUser me, @PathVariable UUID id,
                                                @RequestParam(defaultValue = "0") int page,
                                                @RequestParam(defaultValue = "20") int size) {
        return PageResponse.of(service.accessLog(me, id, page, size));
    }
}
