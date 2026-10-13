package dev.jpzconception.allodocteur.sos.application;

import dev.jpzconception.allodocteur.admin.application.AuditService;
import dev.jpzconception.allodocteur.reference.domain.City;
import dev.jpzconception.allodocteur.reference.domain.District;
import dev.jpzconception.allodocteur.reference.infrastructure.CityRepository;
import dev.jpzconception.allodocteur.reference.infrastructure.DistrictRepository;
import dev.jpzconception.allodocteur.shared.error.ApiException;
import dev.jpzconception.allodocteur.shared.geo.GeolocationProvider;
import dev.jpzconception.allodocteur.sos.api.SosDtos.FacilityDto;
import dev.jpzconception.allodocteur.sos.api.SosDtos.FacilityRequest;
import dev.jpzconception.allodocteur.sos.domain.HealthFacility;
import dev.jpzconception.allodocteur.sos.infrastructure.HealthFacilityRepository;
import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Annuaire des structures de santé (orientation en urgence). Géré par l'administration. */
@Service
@Transactional
public class FacilityService {

    private final HealthFacilityRepository facilities;
    private final CityRepository cities;
    private final DistrictRepository districts;
    private final GeolocationProvider geo;
    private final AuditService audit;

    public FacilityService(HealthFacilityRepository facilities, CityRepository cities, DistrictRepository districts,
                           GeolocationProvider geo, AuditService audit) {
        this.facilities = facilities;
        this.cities = cities;
        this.districts = districts;
        this.geo = geo;
        this.audit = audit;
    }

    /** Structures d'urgence actives les plus proches d'une position, de la plus proche à la plus éloignée. */
    @Transactional(readOnly = true)
    public List<FacilityDto> nearest(BigDecimal latitude, BigDecimal longitude, int limit) {
        List<HealthFacility> all = facilities.findByActiveTrueAndEmergencyServiceTrue();
        record Scored(HealthFacility f, double km) {}
        List<Scored> ranked = all.stream()
                .map(f -> new Scored(f, geo.distanceKm(latitude, longitude, f.getLatitude(), f.getLongitude())))
                .sorted(Comparator.comparingDouble(Scored::km).thenComparing(s -> s.f().getName()))
                .limit(Math.max(limit, 1))
                .toList();
        Names names = names(ranked.stream().map(Scored::f).toList());
        return ranked.stream().map(s -> toDto(s.f(), names, Math.round(s.km() * 10.0) / 10.0)).toList();
    }

    @Transactional(readOnly = true)
    public Page<FacilityDto> list(Integer cityId, boolean activeOnly, int page, int size) {
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100),
                Sort.by("name").and(Sort.by("id")));
        Page<HealthFacility> found = cityId != null ? facilities.searchByCity(cityId, pageable)
                : facilities.search(activeOnly, pageable);
        Names names = names(found.getContent());
        return new PageImpl<>(found.getContent().stream().map(f -> toDto(f, names, null)).toList(), pageable,
                found.getTotalElements());
    }

    public FacilityDto create(UUID adminId, FacilityRequest req) {
        HealthFacility f = new HealthFacility();
        apply(f, req);
        facilities.save(f);
        audit.record(adminId, "FACILITY_CREATED", "HEALTH_FACILITY", f.getId().toString(), f.getName());
        return toDto(f, names(List.of(f)), null);
    }

    public FacilityDto update(UUID adminId, UUID id, FacilityRequest req) {
        HealthFacility f = require(id);
        apply(f, req);
        audit.record(adminId, "FACILITY_UPDATED", "HEALTH_FACILITY", id.toString(), f.getName());
        return toDto(f, names(List.of(f)), null);
    }

    public FacilityDto setActive(UUID adminId, UUID id, boolean active) {
        HealthFacility f = require(id);
        f.setActive(active);
        audit.record(adminId, active ? "FACILITY_ACTIVATED" : "FACILITY_DEACTIVATED", "HEALTH_FACILITY", id.toString(), f.getName());
        return toDto(f, names(List.of(f)), null);
    }

    private void apply(HealthFacility f, FacilityRequest req) {
        City city = cities.findById(req.cityId())
                .orElseThrow(() -> ApiException.badRequest("CITY_NOT_FOUND", "Ville inconnue."));
        if (req.districtId() != null) {
            District d = districts.findById(req.districtId())
                    .orElseThrow(() -> ApiException.badRequest("DISTRICT_NOT_FOUND", "Quartier inconnu."));
            if (!d.getCity().getId().equals(city.getId())) {
                throw ApiException.badRequest("DISTRICT_CITY_MISMATCH", "Ce quartier n'appartient pas à cette ville.");
            }
        }
        f.setName(req.name().trim());
        f.setType(req.type());
        f.setCityId(req.cityId());
        f.setDistrictId(req.districtId());
        f.setAddress(req.address() == null || req.address().isBlank() ? null : req.address().trim());
        f.setPhone(req.phone() == null || req.phone().isBlank() ? null : req.phone().trim());
        f.setLatitude(req.latitude());
        f.setLongitude(req.longitude());
        f.setEmergencyService(req.emergencyService());
    }

    private HealthFacility require(UUID id) {
        return facilities.findById(id)
                .orElseThrow(() -> ApiException.notFound("FACILITY_NOT_FOUND", "Structure de santé introuvable."));
    }

    private record Names(Map<Integer, String> cities, Map<Integer, String> districts) {}

    private Names names(List<HealthFacility> list) {
        Map<Integer, String> c = cities.findAllById(list.stream().map(HealthFacility::getCityId).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(City::getId, City::getName));
        var districtIds = list.stream().map(HealthFacility::getDistrictId).filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Integer, String> d = districts.findAllById(districtIds).stream()
                .collect(Collectors.toMap(District::getId, District::getName, (a, b) -> a));
        return new Names(c, d);
    }

    private static FacilityDto toDto(HealthFacility f, Names n, Double distanceKm) {
        return new FacilityDto(f.getId(), f.getName(), f.getType(), f.getCityId(), n.cities().get(f.getCityId()),
                f.getDistrictId(), f.getDistrictId() == null ? null : n.districts().get(f.getDistrictId()), f.getAddress(),
                f.getPhone(), f.getLatitude(), f.getLongitude(), f.isEmergencyService(), f.isActive(), distanceKm);
    }
}
