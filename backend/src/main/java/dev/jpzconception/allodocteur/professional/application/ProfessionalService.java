package dev.jpzconception.allodocteur.professional.application;

import dev.jpzconception.allodocteur.admin.application.AuditService;
import dev.jpzconception.allodocteur.professional.api.ProfessionalDtos.ProfessionalDetail;
import dev.jpzconception.allodocteur.professional.api.ProfessionalDtos.ProfileRequest;
import dev.jpzconception.allodocteur.professional.api.ProfessionalDtos.ZoneRequest;
import dev.jpzconception.allodocteur.professional.domain.CoverageZone;
import dev.jpzconception.allodocteur.professional.domain.Professional;
import dev.jpzconception.allodocteur.professional.domain.VerificationStatus;
import dev.jpzconception.allodocteur.professional.infrastructure.AvailabilityRuleRepository;
import dev.jpzconception.allodocteur.professional.infrastructure.ProfessionalDocumentRepository;
import dev.jpzconception.allodocteur.professional.infrastructure.ProfessionalRepository;
import dev.jpzconception.allodocteur.reference.domain.City;
import dev.jpzconception.allodocteur.reference.domain.District;
import dev.jpzconception.allodocteur.reference.domain.Specialty;
import dev.jpzconception.allodocteur.reference.infrastructure.ReferenceRepositories.CityRepository;
import dev.jpzconception.allodocteur.reference.infrastructure.ReferenceRepositories.DistrictRepository;
import dev.jpzconception.allodocteur.reference.infrastructure.ReferenceRepositories.ProfessionalTypeRepository;
import dev.jpzconception.allodocteur.reference.infrastructure.ReferenceRepositories.SpecialtyRepository;
import dev.jpzconception.allodocteur.shared.error.ApiException;
import dev.jpzconception.allodocteur.shared.security.AuthenticatedUser;
import dev.jpzconception.allodocteur.user.domain.Role;
import dev.jpzconception.allodocteur.user.domain.UserStatus;
import dev.jpzconception.allodocteur.user.infrastructure.UserRepository;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class ProfessionalService {

    private final ProfessionalRepository professionals;
    private final UserRepository users;
    private final ProfessionalTypeRepository types;
    private final SpecialtyRepository specialties;
    private final CityRepository cities;
    private final DistrictRepository districts;
    private final AvailabilityRuleRepository rules;
    private final ProfessionalDocumentRepository documents;
    private final ProfessionalAssembler assembler;
    private final AuditService audit;

    public ProfessionalService(ProfessionalRepository professionals, UserRepository users,
                               ProfessionalTypeRepository types, SpecialtyRepository specialties,
                               CityRepository cities, DistrictRepository districts,
                               AvailabilityRuleRepository rules, ProfessionalDocumentRepository documents,
                               ProfessionalAssembler assembler, AuditService audit) {
        this.professionals = professionals;
        this.users = users;
        this.types = types;
        this.specialties = specialties;
        this.cities = cities;
        this.districts = districts;
        this.rules = rules;
        this.documents = documents;
        this.assembler = assembler;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public ProfessionalDetail getOwn(UUID userId) {
        return detail(require(userId), true);
    }

    /** Fiche vue par un tiers : visible seulement si approuvée ; propriétaire et admins voient tout. */
    @Transactional(readOnly = true)
    public ProfessionalDetail getVisibleTo(UUID id, AuthenticatedUser viewer) {
        Professional p = require(id);
        boolean privileged = viewer.id().equals(id) || viewer.role() == Role.ADMIN;
        if (!privileged && !(p.isVisibleInDirectory() && p.getUser().getStatus() == UserStatus.ACTIVE)) {
            throw notFound();
        }
        return detail(p, privileged);
    }

    public ProfessionalDetail upsert(UUID userId, ProfileRequest req) {
        validateOffer(req);
        var type = types.findById(req.professionalTypeId())
                .orElseThrow(() -> ApiException.badRequest("INVALID_TYPE", "Type de professionnel inconnu."));
        City city = cities.findById(req.cityId())
                .orElseThrow(() -> ApiException.badRequest("INVALID_CITY", "Ville inconnue."));
        District district = resolveDistrict(city, req.districtId());
        Set<Specialty> chosen = new HashSet<>(specialties.findAllById(req.specialtyIds()));
        if (chosen.size() != req.specialtyIds().size()) {
            throw ApiException.badRequest("INVALID_SPECIALTY", "Spécialité inconnue.");
        }

        String license = req.licenseNumber().trim();
        Professional p = professionals.findById(userId).orElse(null);
        boolean creating = p == null;
        if (creating ? professionals.licenseExists(license) : professionals.licenseUsedByOther(license, userId)) {
            throw ApiException.conflict("LICENSE_ALREADY_USED", "Ce numéro d'ordre est déjà enregistré.");
        }

        boolean sensitiveChange = false;
        if (creating) {
            p = new Professional();
            p.setUser(users.findById(userId)
                    .orElseThrow(() -> ApiException.unauthorized("UNAUTHENTICATED", "Non authentifié.")));
        } else {
            sensitiveChange = !p.getLicenseNumber().equalsIgnoreCase(license)
                    || !p.getType().getId().equals(type.getId());
        }

        p.setType(type);
        p.setQualification(req.qualification().trim());
        p.setLicenseNumber(license);
        p.setYearsExperience(req.yearsExperience());
        p.setBio(req.bio());
        p.setCity(city);
        p.setDistrict(district);
        p.setConsultationFee(req.consultationFee());
        p.setHomeVisitFee(req.homeVisitFee());
        p.setOffersTeleconsultation(req.offersTeleconsultation());
        p.setOffersHomeVisit(req.offersHomeVisit());
        p.getSpecialties().clear();
        p.getSpecialties().addAll(chosen);
        replaceZones(p, req.coverageZones());

        if (!creating) {
            VerificationStatus status = p.getVerificationStatus();
            if (status == VerificationStatus.REJECTED || (status == VerificationStatus.APPROVED && sensitiveChange)) {
                p.resubmit();
                audit.record(userId, "PROFESSIONAL_RESUBMITTED", "PROFESSIONAL", userId.toString(), null);
            }
        }
        professionals.save(p);
        audit.record(userId, creating ? "PROFESSIONAL_CREATED" : "PROFESSIONAL_UPDATED", "PROFESSIONAL",
                userId.toString(), null);
        return detail(p, true);
    }

    public Professional require(UUID id) {
        return professionals.findById(id).orElseThrow(ProfessionalService::notFound);
    }

    public ProfessionalDetail detail(Professional p, boolean privileged) {
        var ruleList = rules.findByProfessionalIdOrderByDayOfWeekAscStartTimeAsc(p.getId());
        var docs = privileged ? documents.findByProfessionalIdOrderByUploadedAtDesc(p.getId()) : List.<dev.jpzconception.allodocteur.professional.domain.ProfessionalDocument>of();
        return assembler.toDetail(p, ruleList, docs, privileged);
    }

    private void replaceZones(Professional p, List<ZoneRequest> requested) {
        p.getCoverageZones().clear();
        if (requested == null) return;
        Set<String> seen = new HashSet<>();
        for (ZoneRequest z : requested) {
            City c = cities.findById(z.cityId())
                    .orElseThrow(() -> ApiException.badRequest("INVALID_CITY", "Ville inconnue."));
            District d = resolveDistrict(c, z.districtId());
            if (seen.add(c.getId() + ":" + (d == null ? "" : d.getId()))) {
                p.getCoverageZones().add(new CoverageZone(p, c, d));
            }
        }
    }

    private District resolveDistrict(City city, Integer districtId) {
        if (districtId == null) return null;
        District d = districts.findById(districtId)
                .orElseThrow(() -> ApiException.badRequest("INVALID_DISTRICT", "Quartier inconnu."));
        if (!d.getCity().getId().equals(city.getId())) {
            throw ApiException.badRequest("INVALID_DISTRICT", "Ce quartier n'appartient pas à la ville indiquée.");
        }
        return d;
    }

    private static void validateOffer(ProfileRequest req) {
        if (!req.offersTeleconsultation() && !req.offersHomeVisit()) {
            throw ApiException.badRequest("NO_SERVICE_OFFERED", "Proposez au moins la téléconsultation ou la visite à domicile.");
        }
        if (req.offersTeleconsultation() && req.consultationFee() == null) {
            throw ApiException.badRequest("FEE_REQUIRED", "Le tarif de téléconsultation est obligatoire.");
        }
        if (req.offersHomeVisit() && req.homeVisitFee() == null) {
            throw ApiException.badRequest("FEE_REQUIRED", "Le tarif de visite à domicile est obligatoire.");
        }
    }

    private static ApiException notFound() {
        return ApiException.notFound("PROFESSIONAL_NOT_FOUND", "Professionnel introuvable.");
    }
}
