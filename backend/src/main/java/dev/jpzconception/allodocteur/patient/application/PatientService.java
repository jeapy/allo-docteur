package dev.jpzconception.allodocteur.patient.application;

import dev.jpzconception.allodocteur.patient.api.PatientDtos.DependentDto;
import dev.jpzconception.allodocteur.patient.api.PatientDtos.DependentRequest;
import dev.jpzconception.allodocteur.patient.api.PatientDtos.PatientDto;
import dev.jpzconception.allodocteur.patient.api.PatientDtos.ProfileRequest;
import dev.jpzconception.allodocteur.patient.domain.Dependent;
import dev.jpzconception.allodocteur.patient.domain.Patient;
import dev.jpzconception.allodocteur.patient.infrastructure.DependentRepository;
import dev.jpzconception.allodocteur.patient.infrastructure.PatientRepository;
import dev.jpzconception.allodocteur.reference.domain.City;
import dev.jpzconception.allodocteur.reference.domain.District;
import dev.jpzconception.allodocteur.reference.infrastructure.CityRepository;
import dev.jpzconception.allodocteur.reference.infrastructure.DistrictRepository;
import dev.jpzconception.allodocteur.shared.error.ApiException;
import dev.jpzconception.allodocteur.user.domain.User;
import dev.jpzconception.allodocteur.user.infrastructure.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Profil du patient connecté et gestion de ses personnes à charge. */
@Service
@Transactional
public class PatientService {

    static final int MAX_DEPENDENTS = 10;

    private final PatientRepository patients;
    private final DependentRepository dependents;
    private final UserRepository users;
    private final CityRepository cities;
    private final DistrictRepository districts;
    private final Clock clock;

    public PatientService(PatientRepository patients, DependentRepository dependents, UserRepository users,
                          CityRepository cities, DistrictRepository districts, Clock clock) {
        this.patients = patients;
        this.dependents = dependents;
        this.users = users;
        this.cities = cities;
        this.districts = districts;
        this.clock = clock;
    }

    /** Renvoie le profil ; un patient qui n'a encore rien renseigné obtient un profil vide. */
    @Transactional(readOnly = true)
    public PatientDto getOwn(UUID userId) {
        return toDto(requireUser(userId), patients.findById(userId).orElse(null));
    }

    public PatientDto upsert(UUID userId, ProfileRequest req) {
        User user = requireUser(userId);
        City city = null;
        District district = null;
        if (req.cityId() != null) {
            city = cities.findById(req.cityId())
                    .orElseThrow(() -> ApiException.badRequest("INVALID_CITY", "Ville inconnue."));
        }
        if (req.districtId() != null) {
            if (city == null) {
                throw ApiException.badRequest("INVALID_DISTRICT", "Indiquez la ville du quartier.");
            }
            district = districts.findById(req.districtId())
                    .filter(d -> d.getCity().getId().equals(req.cityId()))
                    .orElseThrow(() -> ApiException.badRequest("INVALID_DISTRICT", "Quartier inconnu pour cette ville."));
        }
        Patient p = patients.findById(userId).orElseGet(() -> new Patient(userId));
        p.setBirthDate(req.birthDate());
        p.setGender(req.gender());
        p.setCity(city);
        p.setDistrict(district);
        p.setAddress(blankToNull(req.address()));
        p.setEmergencyContactName(blankToNull(req.emergencyContactName()));
        p.setEmergencyContactPhone(blankToNull(req.emergencyContactPhone()));
        patients.save(p);
        return toDto(user, p);
    }

    // ---------------------------------------------------------- personnes à charge

    @Transactional(readOnly = true)
    public List<DependentDto> listDependents(UUID guardianId) {
        return dependents.findByGuardianIdOrderByFirstNameAsc(guardianId).stream().map(PatientService::toDto).toList();
    }

    public DependentDto addDependent(UUID guardianId, DependentRequest req) {
        if (dependents.countByGuardianId(guardianId) >= MAX_DEPENDENTS) {
            throw ApiException.conflict("TOO_MANY_DEPENDENTS", "Nombre maximal de personnes à charge atteint.");
        }
        Dependent d = new Dependent();
        d.setGuardianId(guardianId);
        apply(d, req);
        return toDto(dependents.save(d));
    }

    public DependentDto updateDependent(UUID guardianId, UUID id, DependentRequest req) {
        Dependent d = ownDependent(guardianId, id);
        apply(d, req);
        return toDto(d);
    }

    public void deleteDependent(UUID guardianId, UUID id) {
        Dependent d = ownDependent(guardianId, id);
        d.setDeletedAt(Instant.now(clock));
    }

    /** Utilisé par le module rendez-vous : la personne à charge doit appartenir au compte qui réserve. */
    @Transactional(readOnly = true)
    public Dependent requireOwnDependent(UUID guardianId, UUID id) {
        return ownDependent(guardianId, id);
    }

    @Transactional(readOnly = true)
    public Patient findProfile(UUID userId) {
        return patients.findById(userId).orElse(null);
    }

    /** Ville du profil patient, si renseignée. */
    @Transactional(readOnly = true)
    public Integer cityIdOf(UUID accountId) {
        return patients.findById(accountId).map(Patient::getCity).map(c -> c.getId()).orElse(null);
    }

    /** Date de naissance de la personne concernée par un dossier (titulaire ou personne à charge), si connue. */
    @Transactional(readOnly = true)
    public java.time.LocalDate birthDateOf(UUID accountId, UUID dependentId) {
        if (dependentId != null) return dependents.findById(dependentId).map(Dependent::getBirthDate).orElse(null);
        return patients.findById(accountId).map(Patient::getBirthDate).orElse(null);
    }

    /** Nom de la personne concernée par un dossier : le titulaire du compte ou sa personne à charge. */
    @Transactional(readOnly = true)
    public String beneficiaryName(UUID accountId, UUID dependentId) {
        if (dependentId != null) {
            return dependents.findById(dependentId).map(Dependent::fullName).orElse("Personne supprimée");
        }
        return users.findById(accountId)
                .map(u -> ((u.getFirstName() == null ? "" : u.getFirstName()) + " "
                        + (u.getLastName() == null ? "" : u.getLastName())).trim())
                .orElse("Compte supprimé");
    }

    private Dependent ownDependent(UUID guardianId, UUID id) {
        return dependents.findByIdAndGuardianId(id, guardianId)
                .orElseThrow(() -> ApiException.notFound("DEPENDENT_NOT_FOUND", "Personne à charge introuvable."));
    }

    private static void apply(Dependent d, DependentRequest req) {
        d.setFirstName(req.firstName().trim());
        d.setLastName(req.lastName().trim());
        d.setBirthDate(req.birthDate());
        d.setGender(req.gender());
        d.setRelationship(req.relationship());
    }

    private User requireUser(UUID userId) {
        return users.findById(userId)
                .orElseThrow(() -> ApiException.unauthorized("UNAUTHENTICATED", "Non authentifié."));
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private static PatientDto toDto(User u, Patient p) {
        if (p == null) {
            return new PatientDto(u.getId(), u.getFirstName(), u.getLastName(), u.getEmail(), u.getPhone(),
                    null, null, null, null, null, null, null, null, null, false);
        }
        City c = p.getCity();
        District d = p.getDistrict();
        return new PatientDto(u.getId(), u.getFirstName(), u.getLastName(), u.getEmail(), u.getPhone(),
                p.getBirthDate(), p.getGender(),
                c == null ? null : c.getId(), c == null ? null : c.getName(),
                d == null ? null : d.getId(), d == null ? null : d.getName(),
                p.getAddress(), p.getEmergencyContactName(), p.getEmergencyContactPhone(), p.isPremium());
    }

    private static DependentDto toDto(Dependent d) {
        return new DependentDto(d.getId(), d.getFirstName(), d.getLastName(), d.getBirthDate(), d.getGender(),
                d.getRelationship());
    }
}
