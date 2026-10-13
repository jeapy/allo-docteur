package dev.jpzconception.allodocteur.partner.application;

import dev.jpzconception.allodocteur.partner.domain.InsurancePolicy;
import dev.jpzconception.allodocteur.partner.domain.Partner;
import dev.jpzconception.allodocteur.partner.domain.PartnerStatus;
import dev.jpzconception.allodocteur.partner.domain.PartnerType;
import dev.jpzconception.allodocteur.partner.infrastructure.InsurancePolicyRepository;
import dev.jpzconception.allodocteur.partner.infrastructure.PartnerRepository;
import dev.jpzconception.allodocteur.patient.infrastructure.DependentRepository;
import dev.jpzconception.allodocteur.shared.error.ApiException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Polices d'assurance enregistrées par un patient. Aucun calcul de prise en charge n'est fait sur les paiements. */
@Service
@Transactional
public class InsurancePolicyService {

    public record PolicyView(UUID id, UUID dependentId, UUID partnerId, String partnerName, String policyNumber,
                             BigDecimal coverageRate, LocalDate validUntil, boolean expired) {}

    public record PolicyCommand(UUID dependentId, UUID partnerId, String policyNumber, BigDecimal coverageRate,
                                LocalDate validUntil) {}

    private final InsurancePolicyRepository policies;
    private final PartnerRepository partners;
    private final DependentRepository dependents;
    private final Clock clock;

    public InsurancePolicyService(InsurancePolicyRepository policies, PartnerRepository partners,
                                  DependentRepository dependents, Clock clock) {
        this.policies = policies;
        this.partners = partners;
        this.dependents = dependents;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<PolicyView> list(UUID accountId) {
        return policies.findByAccountIdOrderByValidUntilDesc(accountId).stream().map(this::view).toList();
    }

    @Transactional(readOnly = true)
    public PolicyView get(UUID accountId, UUID id) {
        return view(owned(accountId, id));
    }

    public PolicyView create(UUID accountId, PolicyCommand cmd) {
        InsurancePolicy p = new InsurancePolicy();
        p.setAccountId(accountId);
        apply(accountId, p, cmd);
        policies.save(p);
        return view(p);
    }

    public PolicyView update(UUID accountId, UUID id, PolicyCommand cmd) {
        InsurancePolicy p = owned(accountId, id);
        apply(accountId, p, cmd);
        return view(p);
    }

    public void delete(UUID accountId, UUID id) {
        policies.delete(owned(accountId, id));
    }

    private void apply(UUID accountId, InsurancePolicy p, PolicyCommand c) {
        Partner partner = partners.findById(c.partnerId())
                .filter(x -> x.getStatus() == PartnerStatus.ACTIVE && PartnerType.COVERAGE.contains(x.getType()))
                .orElseThrow(() -> ApiException.badRequest("INVALID_PARTNER", "Assureur ou mutuelle introuvable."));
        if (c.dependentId() != null && dependents.findByIdAndGuardianId(c.dependentId(), accountId).isEmpty()) {
            throw ApiException.notFound("DEPENDENT_NOT_FOUND", "Personne à charge introuvable.");
        }
        if (c.validUntil().isBefore(LocalDate.now(clock))) {
            throw ApiException.badRequest("POLICY_EXPIRED", "La date de validité est dépassée.");
        }
        p.setPartnerId(partner.getId());
        p.setDependentId(c.dependentId());
        p.setPolicyNumber(c.policyNumber().strip());
        p.setCoverageRate(c.coverageRate());
        p.setValidUntil(c.validUntil());
    }

    private InsurancePolicy owned(UUID accountId, UUID id) {
        return policies.findByIdAndAccountId(id, accountId)
                .orElseThrow(() -> ApiException.notFound("POLICY_NOT_FOUND", "Police introuvable."));
    }

    private PolicyView view(InsurancePolicy p) {
        String name = partners.findById(p.getPartnerId()).map(Partner::getName).orElse(null);
        return new PolicyView(p.getId(), p.getDependentId(), p.getPartnerId(), name, p.getPolicyNumber(), p.getCoverageRate(),
                p.getValidUntil(), p.getValidUntil().isBefore(LocalDate.now(clock)));
    }
}
