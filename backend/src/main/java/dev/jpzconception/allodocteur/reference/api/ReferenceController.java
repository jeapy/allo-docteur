package dev.jpzconception.allodocteur.reference.api;

import dev.jpzconception.allodocteur.reference.domain.District;
import dev.jpzconception.allodocteur.reference.infrastructure.CityRepository;
import dev.jpzconception.allodocteur.reference.infrastructure.DistrictRepository;
import dev.jpzconception.allodocteur.reference.infrastructure.ProfessionalTypeRepository;
import dev.jpzconception.allodocteur.reference.infrastructure.SpecialtyRepository;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Référentiels en lecture seule (utilisateurs authentifiés). */
@RestController
@RequestMapping("/api/v1/reference")
@Tag(name = "Référentiels")
public class ReferenceController {

    public record TypeDto(Integer id, String code, String label) {}
    public record SpecialtyDto(Integer id, String code, String name) {}
    public record DistrictDto(Integer id, String name) {}
    public record CityDto(Integer id, String name, List<DistrictDto> districts) {}

    private final ProfessionalTypeRepository types;
    private final SpecialtyRepository specialties;
    private final CityRepository cities;
    private final DistrictRepository districts;

    public ReferenceController(ProfessionalTypeRepository types, SpecialtyRepository specialties,
                               CityRepository cities, DistrictRepository districts) {
        this.types = types;
        this.specialties = specialties;
        this.cities = cities;
        this.districts = districts;
    }

    @GetMapping("/professional-types")
    public List<TypeDto> professionalTypes() {
        return types.findAll().stream().map(t -> new TypeDto(t.getId(), t.getCode(), t.getLabelFr())).toList();
    }

    @GetMapping("/specialties")
    public List<SpecialtyDto> specialties() {
        return specialties.findByActiveTrueOrderByNameFrAsc().stream()
                .map(s -> new SpecialtyDto(s.getId(), s.getCode(), s.getNameFr())).toList();
    }

    @GetMapping("/cities")
    @Transactional(readOnly = true)
    public List<CityDto> cities() {
        Map<Integer, List<DistrictDto>> byCity = districts.findByActiveTrueOrderByNameAsc().stream()
                .collect(Collectors.groupingBy(d -> d.getCity().getId(),
                        Collectors.mapping(ReferenceController::toDto, Collectors.toList())));
        return cities.findByActiveTrueOrderByNameAsc().stream()
                .map(c -> new CityDto(c.getId(), c.getName(), byCity.getOrDefault(c.getId(), List.of())))
                .toList();
    }

    private static DistrictDto toDto(District d) {
        return new DistrictDto(d.getId(), d.getName());
    }
}
