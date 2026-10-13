package dev.jpzconception.allodocteur.partner.api;

import dev.jpzconception.allodocteur.partner.api.PartnerDtos.PartnerRequest;
import dev.jpzconception.allodocteur.partner.application.PartnerService;
import dev.jpzconception.allodocteur.partner.application.PartnerService.PartnerCommand;
import dev.jpzconception.allodocteur.partner.application.PartnerService.PartnerView;
import dev.jpzconception.allodocteur.partner.domain.PartnerStatus;
import dev.jpzconception.allodocteur.partner.domain.PartnerType;
import dev.jpzconception.allodocteur.shared.security.AuthenticatedUser;
import dev.jpzconception.allodocteur.shared.web.PageResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Gestion des partenaires (support, super-administrateur). */
@RestController
@RequestMapping("/api/v1/admin/partners")
@PreAuthorize("hasAnyAuthority('LEVEL_SUPER', 'LEVEL_SUPPORT')")
@Tag(name = "Administration – Partenaires")
public class AdminPartnerController {

    private final PartnerService partners;

    public AdminPartnerController(PartnerService partners) {
        this.partners = partners;
    }

    @GetMapping
    public PageResponse<PartnerView> list(@RequestParam(required = false) PartnerStatus status,
                                          @RequestParam(required = false) PartnerType type,
                                          @RequestParam(required = false) Integer cityId,
                                          @RequestParam(defaultValue = "0") int page,
                                          @RequestParam(defaultValue = "20") int size) {
        return PageResponse.of(partners.adminList(status, type, cityId, page, size));
    }

    @GetMapping("/{id}")
    public PartnerView get(@PathVariable UUID id) {
        return partners.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PartnerView create(@AuthenticationPrincipal AuthenticatedUser me, @Valid @RequestBody PartnerRequest r) {
        return partners.create(me, cmd(r));
    }

    @PutMapping("/{id}")
    public PartnerView update(@AuthenticationPrincipal AuthenticatedUser me, @PathVariable UUID id,
                              @Valid @RequestBody PartnerRequest r) {
        return partners.update(me, id, cmd(r));
    }

    @PostMapping("/{id}/activate")
    public PartnerView activate(@AuthenticationPrincipal AuthenticatedUser me, @PathVariable UUID id) {
        return partners.setStatus(me, id, PartnerStatus.ACTIVE);
    }

    @PostMapping("/{id}/deactivate")
    public PartnerView deactivate(@AuthenticationPrincipal AuthenticatedUser me, @PathVariable UUID id) {
        return partners.setStatus(me, id, PartnerStatus.INACTIVE);
    }

    private static PartnerCommand cmd(PartnerRequest r) {
        return new PartnerCommand(r.name(), r.type(), r.cityId(), r.address(), r.contactEmail(), r.contactPhone(),
                r.contractStartDate());
    }
}
