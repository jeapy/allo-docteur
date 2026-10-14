package dev.jpzconception.allodocteur.reference.application;

import dev.jpzconception.allodocteur.admin.application.AuditService;
import dev.jpzconception.allodocteur.reference.domain.City;
import dev.jpzconception.allodocteur.reference.domain.District;
import dev.jpzconception.allodocteur.reference.domain.ProfessionalType;
import dev.jpzconception.allodocteur.reference.domain.Specialty;
import dev.jpzconception.allodocteur.reference.infrastructure.CityRepository;
import dev.jpzconception.allodocteur.reference.infrastructure.DistrictRepository;
import dev.jpzconception.allodocteur.reference.infrastructure.ProfessionalTypeRepository;
import dev.jpzconception.allodocteur.reference.infrastructure.SpecialtyRepository;
import dev.jpzconception.allodocteur.shared.error.ApiException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Gestion des référentiels par l'administration. Rien n'est supprimé : ces valeurs sont référencées par des
 * professionnels, patients, pharmacies… On les désactive (elles disparaissent des listes publiques, l'existant est conservé).
 */
@Service
@Transactional
public class ReferenceAdminService {

    private static final String DEFAULT_COUNTRY = "CI";

    public record DistrictView(Integer id, String name, boolean active) {}
    public record CityView(Integer id, String name, String countryCode, boolean active, List<DistrictView> districts) {}
    public record SpecialtyView(Integer id, String code, String name, boolean active) {}
    public record TypeView(Integer id, String code, String label) {}

    private final CityRepository cities;
    private final DistrictRepository districts;
    private final SpecialtyRepository specialties;
    private final ProfessionalTypeRepository types;
    private final AuditService audit;

    public ReferenceAdminService(CityRepository cities, DistrictRepository districts, SpecialtyRepository specialties,
                                 ProfessionalTypeRepository types, AuditService audit) {
        this.cities = cities;
        this.districts = districts;
        this.specialties = specialties;
        this.types = types;
        this.audit = audit;
    }

    // ------------------------------------------------------------------ villes et quartiers

    @Transactional(readOnly = true)
    public List<CityView> listCities() {
        Map<Integer, List<DistrictView>> byCity = districts.findAllByOrderByNameAsc().stream()
                .collect(Collectors.groupingBy(d -> d.getCity().getId(),
                        Collectors.mapping(d -> new DistrictView(d.getId(), d.getName(), d.isActive()), Collectors.toList())));
        return cities.findAllByOrderByNameAsc().stream().map(c -> toView(c, byCity.getOrDefault(c.getId(), List.of()))).toList();
    }

    public CityView createCity(UUID adminId, String rawName) {
        String name = clean(rawName);
        if (cities.existsByCountryCodeAndNameIgnoreCase(DEFAULT_COUNTRY, name)) {
            throw ApiException.conflict("CITY_EXISTS", "Cette ville existe déjà.");
        }
        City c = new City();
        c.setName(name);
        c.setCountryCode(DEFAULT_COUNTRY);
        cities.save(c);
        audit.record(adminId, "CITY_CREATED", "CITY", String.valueOf(c.getId()), name);
        return toView(c, List.of());
    }

    public CityView renameCity(UUID adminId, Integer id, String rawName) {
        City c = city(id);
        String name = clean(rawName);
        if (!c.getName().equalsIgnoreCase(name) && cities.existsByCountryCodeAndNameIgnoreCase(c.getCountryCode(), name)) {
            throw ApiException.conflict("CITY_EXISTS", "Cette ville existe déjà.");
        }
        audit.record(adminId, "CITY_RENAMED", "CITY", String.valueOf(id), c.getName() + " -> " + name);
        c.setName(name);
        return view(c);
    }

    public CityView setCityActive(UUID adminId, Integer id, boolean active) {
        City c = city(id);
        c.setActive(active);
        audit.record(adminId, active ? "CITY_ACTIVATED" : "CITY_DEACTIVATED", "CITY", String.valueOf(id), c.getName());
        return view(c);
    }

    public CityView createDistrict(UUID adminId, Integer cityId, String rawName) {
        City c = city(cityId);
        String name = clean(rawName);
        if (districts.existsByCityIdAndNameIgnoreCase(cityId, name)) {
            throw ApiException.conflict("DISTRICT_EXISTS", "Ce quartier existe déjà dans cette ville.");
        }
        District d = new District();
        d.setCity(c);
        d.setName(name);
        districts.save(d);
        audit.record(adminId, "DISTRICT_CREATED", "DISTRICT", String.valueOf(d.getId()), c.getName() + " / " + name);
        return view(c);
    }

    public CityView renameDistrict(UUID adminId, Integer id, String rawName) {
        District d = district(id);
        String name = clean(rawName);
        if (!d.getName().equalsIgnoreCase(name) && districts.existsByCityIdAndNameIgnoreCase(d.getCity().getId(), name)) {
            throw ApiException.conflict("DISTRICT_EXISTS", "Ce quartier existe déjà dans cette ville.");
        }
        audit.record(adminId, "DISTRICT_RENAMED", "DISTRICT", String.valueOf(id), d.getName() + " -> " + name);
        d.setName(name);
        return view(d.getCity());
    }

    public CityView setDistrictActive(UUID adminId, Integer id, boolean active) {
        District d = district(id);
        d.setActive(active);
        audit.record(adminId, active ? "DISTRICT_ACTIVATED" : "DISTRICT_DEACTIVATED", "DISTRICT", String.valueOf(id), d.getName());
        return view(d.getCity());
    }

    // ------------------------------------------------------------------ spécialités

    @Transactional(readOnly = true)
    public List<SpecialtyView> listSpecialties() {
        return specialties.findAllByOrderByNameFrAsc().stream().map(ReferenceAdminService::toView).toList();
    }

    public SpecialtyView createSpecialty(UUID adminId, String rawCode, String rawName) {
        String code = code(rawCode);
        if (specialties.existsByCodeIgnoreCase(code)) {
            throw ApiException.conflict("SPECIALTY_EXISTS", "Ce code de spécialité existe déjà.");
        }
        Specialty s = new Specialty();
        s.setCode(code);
        s.setNameFr(clean(rawName));
        specialties.save(s);
        audit.record(adminId, "SPECIALTY_CREATED", "SPECIALTY", String.valueOf(s.getId()), code);
        return toView(s);
    }

    public SpecialtyView renameSpecialty(UUID adminId, Integer id, String rawName) {
        Specialty s = specialty(id);
        String name = clean(rawName);
        audit.record(adminId, "SPECIALTY_RENAMED", "SPECIALTY", String.valueOf(id), s.getNameFr() + " -> " + name);
        s.setNameFr(name);
        return toView(s);
    }

    public SpecialtyView setSpecialtyActive(UUID adminId, Integer id, boolean active) {
        Specialty s = specialty(id);
        s.setActive(active);
        audit.record(adminId, active ? "SPECIALTY_ACTIVATED" : "SPECIALTY_DEACTIVATED", "SPECIALTY", String.valueOf(id), s.getCode());
        return toView(s);
    }

    // ------------------------------------------------------------------ types de professionnels

    @Transactional(readOnly = true)
    public List<TypeView> listTypes() {
        return types.findAll().stream().sorted(java.util.Comparator.comparing(ProfessionalType::getLabelFr))
                .map(ReferenceAdminService::toView).toList();
    }

    public TypeView createType(UUID adminId, String rawCode, String rawLabel) {
        String code = code(rawCode);
        if (types.existsByCodeIgnoreCase(code)) {
            throw ApiException.conflict("TYPE_EXISTS", "Ce code de type existe déjà.");
        }
        ProfessionalType t = new ProfessionalType();
        t.setCode(code);
        t.setLabelFr(clean(rawLabel));
        types.save(t);
        audit.record(adminId, "PROFESSIONAL_TYPE_CREATED", "PROFESSIONAL_TYPE", String.valueOf(t.getId()), code);
        return toView(t);
    }

    /** Le code d'un type est utilisé par les règles métier (ex. PHARMACIST) : seul le libellé se modifie. */
    public TypeView renameType(UUID adminId, Integer id, String rawLabel) {
        ProfessionalType t = types.findById(id)
                .orElseThrow(() -> ApiException.notFound("TYPE_NOT_FOUND", "Type de professionnel introuvable."));
        String label = clean(rawLabel);
        audit.record(adminId, "PROFESSIONAL_TYPE_RENAMED", "PROFESSIONAL_TYPE", String.valueOf(id), t.getLabelFr() + " -> " + label);
        t.setLabelFr(label);
        return toView(t);
    }

    // ------------------------------------------------------------------ utilitaires

    private City city(Integer id) {
        return cities.findById(id).orElseThrow(() -> ApiException.notFound("CITY_NOT_FOUND", "Ville introuvable."));
    }

    private District district(Integer id) {
        return districts.findById(id).orElseThrow(() -> ApiException.notFound("DISTRICT_NOT_FOUND", "Quartier introuvable."));
    }

    private Specialty specialty(Integer id) {
        return specialties.findById(id)
                .orElseThrow(() -> ApiException.notFound("SPECIALTY_NOT_FOUND", "Spécialité introuvable."));
    }

    private CityView view(City c) {
        List<DistrictView> ds = districts.findByCityIdOrderByNameAsc(c.getId()).stream()
                .map(d -> new DistrictView(d.getId(), d.getName(), d.isActive())).toList();
        return toView(c, ds);
    }

    private static CityView toView(City c, List<DistrictView> ds) {
        return new CityView(c.getId(), c.getName(), c.getCountryCode(), c.isActive(), ds);
    }

    private static SpecialtyView toView(Specialty s) {
        return new SpecialtyView(s.getId(), s.getCode(), s.getNameFr(), s.isActive());
    }

    private static TypeView toView(ProfessionalType t) {
        return new TypeView(t.getId(), t.getCode(), t.getLabelFr());
    }

    private static String clean(String value) {
        String v = value == null ? "" : value.trim().replaceAll("\\s+", " ");
        if (v.isEmpty()) throw ApiException.badRequest("NAME_REQUIRED", "Le libellé est obligatoire.");
        return v;
    }

    private static String code(String value) {
        String v = value == null ? "" : value.trim().toUpperCase();
        if (!v.matches("[A-Z0-9_]{2,50}")) {
            throw ApiException.badRequest("INVALID_CODE", "Code invalide : lettres majuscules, chiffres et _ (2 à 50).");
        }
        return v;
    }
}
