package dev.jpzconception.allodocteur.professional.application;

import dev.jpzconception.allodocteur.professional.infrastructure.ProfessionalRepository;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Façade en lecture seule pour les autres modules (ordonnances, avis…) : identité et qualité d'un professionnel. */
@Service
@Transactional(readOnly = true)
public class ProfessionalLookup {

    public record Summary(UUID id, String fullName, String typeCode, String qualification, String licenseNumber) {}

    /** Un professionnel peut-il répondre à une alerte SOS ? Approuvé, actif et proposant la visite à domicile. */
    public record Dispatcher(UUID id, Integer cityId, String fullName, String phone) {}

    private final ProfessionalRepository professionals;

    public ProfessionalLookup(ProfessionalRepository professionals) {
        this.professionals = professionals;
    }

    public Optional<Summary> find(UUID professionalId) {
        return professionals.findById(professionalId).map(p -> {
            String first = p.getUser().getFirstName() == null ? "" : p.getUser().getFirstName();
            String last = p.getUser().getLastName() == null ? "" : p.getUser().getLastName();
            String name = (first + " " + last).trim();
            return new Summary(p.getId(), name.isEmpty() ? p.getUser().getEmail() : name, p.getType().getCode(),
                    p.getQualification(), p.getLicenseNumber());
        });
    }

    public Optional<Dispatcher> dispatcher(UUID professionalId) {
        return professionals.findById(professionalId)
                .filter(p -> p.isVisibleInDirectory() && p.isOffersHomeVisit()
                        && p.getUser().getStatus() == dev.jpzconception.allodocteur.user.domain.UserStatus.ACTIVE)
                .map(p -> new Dispatcher(p.getId(), p.getCity().getId(), name(p), p.getUser().getPhone()));
    }

    /** Pharmacien(ne) approuvé(e) et actif(ve) : seul(e) à pouvoir être rattaché(e) à une pharmacie. */
    public boolean isActivePharmacist(UUID professionalId) {
        return professionals.findById(professionalId)
                .filter(p -> p.isVisibleInDirectory()
                        && p.getUser().getStatus() == dev.jpzconception.allodocteur.user.domain.UserStatus.ACTIVE)
                .map(p -> "PHARMACIST".equals(p.getType().getCode()))
                .orElse(false);
    }

    private static String name(dev.jpzconception.allodocteur.professional.domain.Professional p) {
        String first = p.getUser().getFirstName() == null ? "" : p.getUser().getFirstName();
        String last = p.getUser().getLastName() == null ? "" : p.getUser().getLastName();
        String full = (first + " " + last).trim();
        return full.isEmpty() ? p.getUser().getEmail() : full;
    }
}
