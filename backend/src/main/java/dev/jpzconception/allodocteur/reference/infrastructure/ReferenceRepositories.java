package dev.jpzconception.allodocteur.reference.infrastructure;

import dev.jpzconception.allodocteur.reference.domain.City;
import dev.jpzconception.allodocteur.reference.domain.District;
import dev.jpzconception.allodocteur.reference.domain.ProfessionalType;
import dev.jpzconception.allodocteur.reference.domain.Specialty;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public final class ReferenceRepositories {

    private ReferenceRepositories() {}

    public interface ProfessionalTypeRepository extends JpaRepository<ProfessionalType, Integer> {}

    public interface SpecialtyRepository extends JpaRepository<Specialty, Integer> {
        List<Specialty> findByActiveTrueOrderByNameFrAsc();
    }

    public interface CityRepository extends JpaRepository<City, Integer> {
        List<City> findByActiveTrueOrderByNameAsc();
    }

    public interface DistrictRepository extends JpaRepository<District, Integer> {
        List<District> findByActiveTrueOrderByNameAsc();
    }
}
