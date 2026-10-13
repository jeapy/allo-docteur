package dev.jpzconception.allodocteur.pharmacy.api;

import dev.jpzconception.allodocteur.pharmacy.api.PharmacyDtos.MemberDto;
import dev.jpzconception.allodocteur.pharmacy.api.PharmacyDtos.PharmacyDto;
import dev.jpzconception.allodocteur.pharmacy.api.PharmacyDtos.PharmacyRequest;
import dev.jpzconception.allodocteur.pharmacy.application.PharmacyService;
import dev.jpzconception.allodocteur.shared.security.AuthenticatedUser;
import dev.jpzconception.allodocteur.shared.web.PageResponse;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Gestion des pharmacies partenaires. Lecture : tout administrateur ; écriture : SUPER et SUPPORT. */
@RestController
@RequestMapping("/api/v1/admin/pharmacies")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Administration – pharmacies")
public class AdminPharmacyController {

    private static final String WRITE = "hasAnyAuthority('LEVEL_SUPER', 'LEVEL_SUPPORT')";

    private final PharmacyService service;

    public AdminPharmacyController(PharmacyService service) {
        this.service = service;
    }

    @GetMapping
    public PageResponse<PharmacyDto> list(@RequestParam(required = false) Integer cityId,
                                          @RequestParam(required = false) Boolean onDuty,
                                          @RequestParam(required = false) String q,
                                          @RequestParam(defaultValue = "0") int page,
                                          @RequestParam(defaultValue = "20") int size) {
        return PageResponse.of(service.search(cityId, null, onDuty, null, q, false, page, size));
    }

    @GetMapping("/{id}")
    public PharmacyDto get(@PathVariable UUID id) {
        return service.get(id);
    }

    @PostMapping
    @PreAuthorize(WRITE)
    @ResponseStatus(HttpStatus.CREATED)
    public PharmacyDto create(@AuthenticationPrincipal AuthenticatedUser me, @Valid @RequestBody PharmacyRequest request) {
        return service.create(me.id(), request);
    }

    @PutMapping("/{id}")
    @PreAuthorize(WRITE)
    public PharmacyDto update(@AuthenticationPrincipal AuthenticatedUser me, @PathVariable UUID id,
                              @Valid @RequestBody PharmacyRequest request) {
        return service.update(me.id(), id, request);
    }

    @PostMapping("/{id}/activate")
    @PreAuthorize(WRITE)
    public PharmacyDto activate(@AuthenticationPrincipal AuthenticatedUser me, @PathVariable UUID id) {
        return service.setActive(me.id(), id, true);
    }

    @PostMapping("/{id}/deactivate")
    @PreAuthorize(WRITE)
    public PharmacyDto deactivate(@AuthenticationPrincipal AuthenticatedUser me, @PathVariable UUID id) {
        return service.setActive(me.id(), id, false);
    }

    @GetMapping("/{id}/members")
    public List<MemberDto> members(@PathVariable UUID id) {
        return service.listMembers(id);
    }

    @PutMapping("/{id}/members/{userId}")
    @PreAuthorize(WRITE)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void addMember(@AuthenticationPrincipal AuthenticatedUser me, @PathVariable UUID id, @PathVariable UUID userId) {
        service.addMember(me.id(), id, userId);
    }

    @DeleteMapping("/{id}/members/{userId}")
    @PreAuthorize(WRITE)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeMember(@AuthenticationPrincipal AuthenticatedUser me, @PathVariable UUID id, @PathVariable UUID userId) {
        service.removeMember(me.id(), id, userId);
    }
}
