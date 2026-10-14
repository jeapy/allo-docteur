package dev.jpzconception.allodocteur.pharmacy.application;

import dev.jpzconception.allodocteur.admin.application.AuditService;
import dev.jpzconception.allodocteur.pharmacy.api.PharmacyDtos.MemberDto;
import dev.jpzconception.allodocteur.pharmacy.api.PharmacyDtos.PharmacyDto;
import dev.jpzconception.allodocteur.pharmacy.api.PharmacyDtos.PharmacyRequest;
import dev.jpzconception.allodocteur.pharmacy.domain.Pharmacy;
import dev.jpzconception.allodocteur.pharmacy.domain.PharmacyMember;
import dev.jpzconception.allodocteur.pharmacy.infrastructure.PharmacyMemberRepository;
import dev.jpzconception.allodocteur.pharmacy.infrastructure.PharmacyRepository;
import dev.jpzconception.allodocteur.pharmacy.infrastructure.PharmacySpecifications;
import dev.jpzconception.allodocteur.professional.application.ProfessionalLookup;
import dev.jpzconception.allodocteur.reference.domain.City;
import dev.jpzconception.allodocteur.reference.domain.District;
import dev.jpzconception.allodocteur.reference.infrastructure.CityRepository;
import dev.jpzconception.allodocteur.reference.infrastructure.DistrictRepository;
import dev.jpzconception.allodocteur.shared.error.ApiException;
import dev.jpzconception.allodocteur.shared.geo.GeolocationProvider;
import dev.jpzconception.allodocteur.shared.geo.NearestSearch;
import dev.jpzconception.allodocteur.user.domain.User;
import dev.jpzconception.allodocteur.user.infrastructure.UserRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
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

/** Annuaire des pharmacies partenaires, et rattachement des pharmaciens (géré par l'administration). */
@Service
@Transactional
public class PharmacyService {

    private final PharmacyRepository pharmacies;
    private final PharmacyMemberRepository members;
    private final CityRepository cities;
    private final DistrictRepository districts;
    private final ProfessionalLookup professionals;
    private final UserRepository users;
    private final GeolocationProvider geo;
    private final AuditService audit;
    private final Clock clock;

    public PharmacyService(PharmacyRepository pharmacies, PharmacyMemberRepository members, CityRepository cities,
                           DistrictRepository districts, ProfessionalLookup professionals, UserRepository users,
                           GeolocationProvider geo, AuditService audit, Clock clock) {
        this.pharmacies = pharmacies;
        this.members = members;
        this.cities = cities;
        this.districts = districts;
        this.professionals = professionals;
        this.users = users;
        this.geo = geo;
        this.audit = audit;
        this.clock = clock;
    }

    // ------------------------------------------------------------------ recherche

    @Transactional(readOnly = true)
    public Page<PharmacyDto> search(Integer cityId, Integer districtId, Boolean onDuty, Boolean delivery, String name,
                                    boolean activeOnly, int page, int size) {
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100),
                Sort.by("name").and(Sort.by("id")));
        Page<Pharmacy> found = pharmacies.findAll(
                PharmacySpecifications.filter(cityId, districtId, onDuty, delivery, name, activeOnly), pageable);
        Names names = names(found.getContent());
        return new PageImpl<>(found.getContent().stream().map(p -> toDto(p, names, null)).toList(), pageable,
                found.getTotalElements());
    }

    /** Pharmacies actives géolocalisées, de la plus proche à la plus éloignée. */
    @Transactional(readOnly = true)
    public List<PharmacyDto> nearby(BigDecimal latitude, BigDecimal longitude, boolean onDutyOnly, int limit) {
        var ranked = NearestSearch.nearest(latitude, longitude, limit, geo,
                box -> pharmacies.findInBox(onDutyOnly, box.minLat(), box.maxLat(), box.minLon(), box.maxLon()),
                () -> pharmacies.findByActiveTrueAndLatitudeIsNotNull().stream()
                        .filter(p -> !onDutyOnly || p.isOnDuty()).toList(),
                Pharmacy::getLatitude, Pharmacy::getLongitude, Comparator.comparing(Pharmacy::getName));
        Names names = names(ranked.stream().map(NearestSearch.Ranked::item).toList());
        return ranked.stream().map(r -> toDto(r.item(), names, Math.round(r.km() * 10.0) / 10.0)).toList();
    }

    @Transactional(readOnly = true)
    public PharmacyDto get(UUID id) {
        Pharmacy p = require(id);
        return toDto(p, names(List.of(p)), null);
    }

    /** Pharmacies auxquelles le pharmacien est rattaché. */
    @Transactional(readOnly = true)
    public List<PharmacyDto> mine(UUID userId) {
        Set<UUID> ids = members.findByUserId(userId).stream().map(PharmacyMember::getPharmacyId).collect(Collectors.toSet());
        List<Pharmacy> list = pharmacies.findAllById(ids);
        Names names = names(list);
        return list.stream().map(p -> toDto(p, names, null)).toList();
    }

    // ------------------------------------------------------------------ administration

    public PharmacyDto create(UUID adminId, PharmacyRequest req) {
        Pharmacy p = new Pharmacy();
        apply(p, req);
        pharmacies.save(p);
        audit.record(adminId, "PHARMACY_CREATED", "PHARMACY", p.getId().toString(), p.getName());
        return toDto(p, names(List.of(p)), null);
    }

    public PharmacyDto update(UUID adminId, UUID id, PharmacyRequest req) {
        Pharmacy p = require(id);
        apply(p, req);
        audit.record(adminId, "PHARMACY_UPDATED", "PHARMACY", id.toString(), p.getName());
        return toDto(p, names(List.of(p)), null);
    }

    public PharmacyDto setActive(UUID adminId, UUID id, boolean active) {
        Pharmacy p = require(id);
        p.setActive(active);
        audit.record(adminId, active ? "PHARMACY_ACTIVATED" : "PHARMACY_DEACTIVATED", "PHARMACY", id.toString(), p.getName());
        return toDto(p, names(List.of(p)), null);
    }

    @Transactional(readOnly = true)
    public List<MemberDto> listMembers(UUID pharmacyId) {
        require(pharmacyId);
        List<PharmacyMember> list = members.findByPharmacyId(pharmacyId);
        Map<UUID, User> byId = users.findAllById(list.stream().map(PharmacyMember::getUserId).toList()).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
        return list.stream().map(m -> {
            User u = byId.get(m.getUserId());
            return new MemberDto(m.getUserId(), name(u), u == null ? null : u.getEmail(), m.getAddedAt());
        }).toList();
    }

    /** Rattache un pharmacien approuvé : lui seul peut traiter les commandes de cette pharmacie. */
    public void addMember(UUID adminId, UUID pharmacyId, UUID userId) {
        require(pharmacyId);
        if (!professionals.isActivePharmacist(userId)) {
            throw ApiException.badRequest("NOT_A_PHARMACIST",
                    "Ce compte n'est pas un pharmacien approuvé et actif.");
        }
        if (!members.existsByPharmacyIdAndUserId(pharmacyId, userId)) {
            members.save(new PharmacyMember(pharmacyId, userId, Instant.now(clock)));
            audit.record(adminId, "PHARMACY_MEMBER_ADDED", "PHARMACY", pharmacyId.toString(), userId.toString());
        }
    }

    public void removeMember(UUID adminId, UUID pharmacyId, UUID userId) {
        require(pharmacyId);
        if (members.existsByPharmacyIdAndUserId(pharmacyId, userId)) {
            members.deleteById(new dev.jpzconception.allodocteur.pharmacy.domain.PharmacyMemberId(pharmacyId, userId));
            audit.record(adminId, "PHARMACY_MEMBER_REMOVED", "PHARMACY", pharmacyId.toString(), userId.toString());
        }
    }

    // ------------------------------------------------------------------ utilitaires

    public Pharmacy require(UUID id) {
        return pharmacies.findById(id)
                .orElseThrow(() -> ApiException.notFound("PHARMACY_NOT_FOUND", "Pharmacie introuvable."));
    }

    private void apply(Pharmacy p, PharmacyRequest req) {
        City city = cities.findById(req.cityId())
                .orElseThrow(() -> ApiException.badRequest("CITY_NOT_FOUND", "Ville inconnue."));
        if (req.districtId() != null) {
            District d = districts.findById(req.districtId())
                    .orElseThrow(() -> ApiException.badRequest("DISTRICT_NOT_FOUND", "Quartier inconnu."));
            if (!d.getCity().getId().equals(city.getId())) {
                throw ApiException.badRequest("DISTRICT_CITY_MISMATCH", "Ce quartier n'appartient pas à cette ville.");
            }
        }
        if ((req.latitude() == null) != (req.longitude() == null)) {
            throw ApiException.badRequest("INVALID_COORDINATES", "Indiquez la latitude et la longitude, ou aucune.");
        }
        if (req.offersDelivery() && req.deliveryFee() == null) {
            throw ApiException.badRequest("DELIVERY_FEE_REQUIRED", "Indiquez les frais de livraison (0 si gratuite).");
        }
        p.setName(req.name().trim());
        p.setCityId(req.cityId());
        p.setDistrictId(req.districtId());
        p.setAddress(req.address().trim());
        p.setPhone(req.phone().trim());
        p.setLatitude(req.latitude());
        p.setLongitude(req.longitude());
        p.setOpeningHours(req.openingHours() == null || req.openingHours().isBlank() ? null : req.openingHours().trim());
        p.setOnDuty(req.onDuty());
        p.setOffersDelivery(req.offersDelivery());
        p.setDeliveryFee(req.offersDelivery() ? req.deliveryFee() : BigDecimal.ZERO);
    }

    private record Names(Map<Integer, String> cities, Map<Integer, String> districts) {}

    private Names names(Collection<Pharmacy> list) {
        Map<Integer, String> c = cities.findAllById(list.stream().map(Pharmacy::getCityId).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(City::getId, City::getName));
        Set<Integer> districtIds = list.stream().map(Pharmacy::getDistrictId).filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Integer, String> d = districts.findAllById(districtIds).stream()
                .collect(Collectors.toMap(District::getId, District::getName, (a, b) -> a));
        return new Names(c, d);
    }

    private static PharmacyDto toDto(Pharmacy p, Names n, Double distanceKm) {
        return new PharmacyDto(p.getId(), p.getName(), p.getCityId(), n.cities().get(p.getCityId()), p.getDistrictId(),
                p.getDistrictId() == null ? null : n.districts().get(p.getDistrictId()), p.getAddress(), p.getPhone(),
                p.getLatitude(), p.getLongitude(), p.getOpeningHours(), p.isOnDuty(), p.isOffersDelivery(),
                p.getDeliveryFee(), p.getCurrency(), p.isActive(), distanceKm);
    }

    private static String name(User u) {
        if (u == null) return "Compte supprimé";
        String full = ((u.getFirstName() == null ? "" : u.getFirstName()) + " "
                + (u.getLastName() == null ? "" : u.getLastName())).trim();
        return full.isEmpty() ? u.getEmail() : full;
    }
}
