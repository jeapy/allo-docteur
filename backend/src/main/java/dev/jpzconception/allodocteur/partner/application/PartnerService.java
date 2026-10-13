package dev.jpzconception.allodocteur.partner.application;

import dev.jpzconception.allodocteur.admin.application.AuditService;
import dev.jpzconception.allodocteur.partner.domain.Partner;
import dev.jpzconception.allodocteur.partner.domain.PartnerStatus;
import dev.jpzconception.allodocteur.partner.domain.PartnerType;
import dev.jpzconception.allodocteur.partner.infrastructure.PartnerRepository;
import dev.jpzconception.allodocteur.partner.infrastructure.PartnerSpecifications;
import dev.jpzconception.allodocteur.reference.infrastructure.CityRepository;
import dev.jpzconception.allodocteur.shared.error.ApiException;
import dev.jpzconception.allodocteur.shared.security.AuthenticatedUser;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Annuaire des partenaires : lecture pour tous les utilisateurs connectés (actifs), gestion par l'administration. */
@Service
@Transactional
public class PartnerService {

    public record PartnerView(UUID id, String name, PartnerType type, Integer cityId, String cityName, String address,
                              String contactEmail, String contactPhone, LocalDate contractStartDate, PartnerStatus status) {}

    public record PartnerCommand(String name, PartnerType type, Integer cityId, String address, String contactEmail,
                                 String contactPhone, LocalDate contractStartDate) {}

    private final PartnerRepository partners;
    private final CityRepository cities;
    private final AuditService audit;

    public PartnerService(PartnerRepository partners, CityRepository cities, AuditService audit) {
        this.partners = partners;
        this.cities = cities;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public Page<PartnerView> directory(PartnerType type, Integer cityId, int page, int size) {
        return page(PartnerSpecifications.of(PartnerStatus.ACTIVE, type, cityId), page, size);
    }

    @Transactional(readOnly = true)
    public Page<PartnerView> adminList(PartnerStatus status, PartnerType type, Integer cityId, int page, int size) {
        return page(PartnerSpecifications.of(status, type, cityId), page, size);
    }

    @Transactional(readOnly = true)
    public PartnerView get(UUID id) {
        return view(find(id));
    }

    public PartnerView create(AuthenticatedUser admin, PartnerCommand cmd) {
        Partner p = new Partner();
        apply(p, cmd);
        partners.save(p);
        audit.record(admin.id(), "PARTNER_CREATED", "PARTNER", p.getId().toString(), p.getName());
        return view(p);
    }

    public PartnerView update(AuthenticatedUser admin, UUID id, PartnerCommand cmd) {
        Partner p = find(id);
        apply(p, cmd);
        audit.record(admin.id(), "PARTNER_UPDATED", "PARTNER", id.toString(), p.getName());
        return view(p);
    }

    public PartnerView setStatus(AuthenticatedUser admin, UUID id, PartnerStatus status) {
        Partner p = find(id);
        p.setStatus(status);
        audit.record(admin.id(), status == PartnerStatus.ACTIVE ? "PARTNER_ACTIVATED" : "PARTNER_DEACTIVATED", "PARTNER",
                id.toString(), p.getName());
        return view(p);
    }

    private void apply(Partner p, PartnerCommand c) {
        if (c.cityId() != null && !cities.existsById(c.cityId())) {
            throw ApiException.badRequest("CITY_NOT_FOUND", "Ville inconnue.");
        }
        p.setName(c.name().strip());
        p.setType(c.type());
        p.setCityId(c.cityId());
        p.setAddress(blankToNull(c.address()));
        p.setContactEmail(blankToNull(c.contactEmail()));
        p.setContactPhone(blankToNull(c.contactPhone()));
        p.setContractStartDate(c.contractStartDate());
    }

    private Page<PartnerView> page(org.springframework.data.jpa.domain.Specification<Partner> spec, int page, int size) {
        var pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100), Sort.by("name"));
        return partners.findAll(spec, pageable).map(this::view);
    }

    private Partner find(UUID id) {
        return partners.findById(id).orElseThrow(() -> ApiException.notFound("PARTNER_NOT_FOUND", "Partenaire introuvable."));
    }

    private PartnerView view(Partner p) {
        String city = p.getCityId() == null ? null : cities.findById(p.getCityId()).map(c -> c.getName()).orElse(null);
        return new PartnerView(p.getId(), p.getName(), p.getType(), p.getCityId(), city, p.getAddress(), p.getContactEmail(),
                p.getContactPhone(), p.getContractStartDate(), p.getStatus());
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.strip();
    }
}
