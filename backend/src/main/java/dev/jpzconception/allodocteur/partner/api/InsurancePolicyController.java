package dev.jpzconception.allodocteur.partner.api;

import dev.jpzconception.allodocteur.partner.api.PartnerDtos.PolicyRequest;
import dev.jpzconception.allodocteur.partner.application.InsurancePolicyService;
import dev.jpzconception.allodocteur.partner.application.InsurancePolicyService.PolicyCommand;
import dev.jpzconception.allodocteur.partner.application.InsurancePolicyService.PolicyView;
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

/** Polices d'assurance / mutuelle du patient connecté. */
@RestController
@RequestMapping("/api/v1/insurance-policies")
@PreAuthorize("hasRole('PATIENT')")
@Tag(name = "Assurances")
public class InsurancePolicyController {

    private final InsurancePolicyService policies;

    public InsurancePolicyController(InsurancePolicyService policies) {
        this.policies = policies;
    }

    @GetMapping
    public List<PolicyView> list(@AuthenticationPrincipal AuthenticatedUser me) {
        return policies.list(me.id());
    }

    @GetMapping("/{id}")
    public PolicyView get(@AuthenticationPrincipal AuthenticatedUser me, @PathVariable UUID id) {
        return policies.get(me.id(), id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PolicyView create(@AuthenticationPrincipal AuthenticatedUser me, @Valid @RequestBody PolicyRequest r) {
        return policies.create(me.id(), cmd(r));
    }

    @PutMapping("/{id}")
    public PolicyView update(@AuthenticationPrincipal AuthenticatedUser me, @PathVariable UUID id,
                             @Valid @RequestBody PolicyRequest r) {
        return policies.update(me.id(), id, cmd(r));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal AuthenticatedUser me, @PathVariable UUID id) {
        policies.delete(me.id(), id);
    }

    private static PolicyCommand cmd(PolicyRequest r) {
        return new PolicyCommand(r.dependentId(), r.partnerId(), r.policyNumber(), r.coverageRate(), r.validUntil());
    }
}
