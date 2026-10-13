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
}
