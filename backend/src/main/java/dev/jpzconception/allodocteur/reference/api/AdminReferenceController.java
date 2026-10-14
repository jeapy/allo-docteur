package dev.jpzconception.allodocteur.reference.api;

import dev.jpzconception.allodocteur.reference.application.ReferenceAdminService;
import dev.jpzconception.allodocteur.reference.application.ReferenceAdminService.CityView;
import dev.jpzconception.allodocteur.reference.application.ReferenceAdminService.SpecialtyView;
import dev.jpzconception.allodocteur.reference.application.ReferenceAdminService.TypeView;
import dev.jpzconception.allodocteur.shared.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Référentiels (villes, quartiers, spécialités, types) : réservé au niveau SUPER, car ils touchent toute la plateforme. */
@RestController
@RequestMapping("/api/v1/admin/reference")
@PreAuthorize("hasRole('ADMIN') and hasAuthority('LEVEL_SUPER')")
@Tag(name = "Admin – référentiels")
public class AdminReferenceController {

    public record NameRequest(@NotBlank @Size(max = 120) String name) {}
    public record CodedRequest(@NotBlank @Size(max = 60) String code, @NotBlank @Size(max = 120) String name) {}

    private final ReferenceAdminService service;

    public AdminReferenceController(ReferenceAdminService service) {
        this.service = service;
    }

    // villes et quartiers

    @GetMapping("/cities")
    public List<CityView> cities() {
        return service.listCities();
    }

    @PostMapping("/cities")
    @ResponseStatus(HttpStatus.CREATED)
    public CityView createCity(@AuthenticationPrincipal AuthenticatedUser me, @Valid @RequestBody NameRequest r) {
        return service.createCity(me.id(), r.name());
    }

    @PutMapping("/cities/{id}")
    public CityView renameCity(@AuthenticationPrincipal AuthenticatedUser me, @PathVariable Integer id,
                               @Valid @RequestBody NameRequest r) {
        return service.renameCity(me.id(), id, r.name());
    }

    @PostMapping("/cities/{id}/activate")
    public CityView activateCity(@AuthenticationPrincipal AuthenticatedUser me, @PathVariable Integer id) {
        return service.setCityActive(me.id(), id, true);
    }

    @PostMapping("/cities/{id}/deactivate")
    public CityView deactivateCity(@AuthenticationPrincipal AuthenticatedUser me, @PathVariable Integer id) {
        return service.setCityActive(me.id(), id, false);
    }

    @PostMapping("/cities/{id}/districts")
    @ResponseStatus(HttpStatus.CREATED)
    public CityView createDistrict(@AuthenticationPrincipal AuthenticatedUser me, @PathVariable Integer id,
                                   @Valid @RequestBody NameRequest r) {
        return service.createDistrict(me.id(), id, r.name());
    }

    @PutMapping("/districts/{id}")
    public CityView renameDistrict(@AuthenticationPrincipal AuthenticatedUser me, @PathVariable Integer id,
                                   @Valid @RequestBody NameRequest r) {
        return service.renameDistrict(me.id(), id, r.name());
    }

    @PostMapping("/districts/{id}/activate")
    public CityView activateDistrict(@AuthenticationPrincipal AuthenticatedUser me, @PathVariable Integer id) {
        return service.setDistrictActive(me.id(), id, true);
    }

    @PostMapping("/districts/{id}/deactivate")
    public CityView deactivateDistrict(@AuthenticationPrincipal AuthenticatedUser me, @PathVariable Integer id) {
        return service.setDistrictActive(me.id(), id, false);
    }

    // spécialités

    @GetMapping("/specialties")
    public List<SpecialtyView> specialties() {
        return service.listSpecialties();
    }

    @PostMapping("/specialties")
    @ResponseStatus(HttpStatus.CREATED)
    public SpecialtyView createSpecialty(@AuthenticationPrincipal AuthenticatedUser me, @Valid @RequestBody CodedRequest r) {
        return service.createSpecialty(me.id(), r.code(), r.name());
    }

    @PutMapping("/specialties/{id}")
    public SpecialtyView renameSpecialty(@AuthenticationPrincipal AuthenticatedUser me, @PathVariable Integer id,
                                         @Valid @RequestBody NameRequest r) {
        return service.renameSpecialty(me.id(), id, r.name());
    }

    @PostMapping("/specialties/{id}/activate")
    public SpecialtyView activateSpecialty(@AuthenticationPrincipal AuthenticatedUser me, @PathVariable Integer id) {
        return service.setSpecialtyActive(me.id(), id, true);
    }

    @PostMapping("/specialties/{id}/deactivate")
    public SpecialtyView deactivateSpecialty(@AuthenticationPrincipal AuthenticatedUser me, @PathVariable Integer id) {
        return service.setSpecialtyActive(me.id(), id, false);
    }

    // types de professionnels

    @GetMapping("/professional-types")
    public List<TypeView> types() {
        return service.listTypes();
    }

    @PostMapping("/professional-types")
    @ResponseStatus(HttpStatus.CREATED)
    public TypeView createType(@AuthenticationPrincipal AuthenticatedUser me, @Valid @RequestBody CodedRequest r) {
        return service.createType(me.id(), r.code(), r.name());
    }

    @PutMapping("/professional-types/{id}")
    public TypeView renameType(@AuthenticationPrincipal AuthenticatedUser me, @PathVariable Integer id,
                               @Valid @RequestBody NameRequest r) {
        return service.renameType(me.id(), id, r.name());
    }
}
