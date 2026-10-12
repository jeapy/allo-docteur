package dev.jpzconception.allodocteur.patient.api;

import dev.jpzconception.allodocteur.patient.api.PatientDtos.DependentDto;
import dev.jpzconception.allodocteur.patient.api.PatientDtos.DependentRequest;
import dev.jpzconception.allodocteur.patient.api.PatientDtos.PatientDto;
import dev.jpzconception.allodocteur.patient.api.PatientDtos.ProfileRequest;
import dev.jpzconception.allodocteur.patient.application.PatientService;
import dev.jpzconception.allodocteur.shared.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Espace du patient connecté : profil et personnes à charge. */
@RestController
@RequestMapping("/api/v1/patients/me")
@PreAuthorize("hasRole('PATIENT')")
@Tag(name = "Patient – mon espace")
public class PatientController {

    private final PatientService service;

    public PatientController(PatientService service) {
        this.service = service;
    }

    @GetMapping
    public PatientDto get(@AuthenticationPrincipal AuthenticatedUser me) {
        return service.getOwn(me.id());
    }

    @PutMapping
    public PatientDto save(@AuthenticationPrincipal AuthenticatedUser me, @Valid @RequestBody ProfileRequest request) {
        return service.upsert(me.id(), request);
    }

    @GetMapping("/dependents")
    public List<DependentDto> dependents(@AuthenticationPrincipal AuthenticatedUser me) {
        return service.listDependents(me.id());
    }

    @PostMapping("/dependents")
    @ResponseStatus(HttpStatus.CREATED)
    public DependentDto addDependent(@AuthenticationPrincipal AuthenticatedUser me,
                                     @Valid @RequestBody DependentRequest request) {
        return service.addDependent(me.id(), request);
    }

    @PutMapping("/dependents/{id}")
    public DependentDto updateDependent(@AuthenticationPrincipal AuthenticatedUser me, @PathVariable UUID id,
                                        @Valid @RequestBody DependentRequest request) {
        return service.updateDependent(me.id(), id, request);
    }

    @DeleteMapping("/dependents/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteDependent(@AuthenticationPrincipal AuthenticatedUser me, @PathVariable UUID id) {
        service.deleteDependent(me.id(), id);
    }
}
