package dev.jpzconception.allodocteur.patient.api;

import dev.jpzconception.allodocteur.patient.domain.Gender;
import dev.jpzconception.allodocteur.patient.domain.Relationship;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.UUID;

public final class PatientDtos {

    private PatientDtos() {}

    private static final String PHONE = "^\\+?[0-9 ]{8,20}$";

    public record ProfileRequest(
            @Past LocalDate birthDate,
            Gender gender,
            Integer cityId,
            Integer districtId,
            @Size(max = 255) String address,
            @Size(max = 150) String emergencyContactName,
            @Pattern(regexp = PHONE, message = "Numéro de téléphone invalide") String emergencyContactPhone) {}

    public record DependentRequest(
            @NotBlank @Size(max = 100) String firstName,
            @NotBlank @Size(max = 100) String lastName,
            @NotNull @Past LocalDate birthDate,
            Gender gender,
            @NotNull Relationship relationship) {}

    public record PatientDto(
            UUID userId,
            String firstName,
            String lastName,
            String email,
            String phone,
            LocalDate birthDate,
            Gender gender,
            Integer cityId,
            String city,
            Integer districtId,
            String district,
            String address,
            String emergencyContactName,
            String emergencyContactPhone,
            boolean premium) {}

    public record DependentDto(
            UUID id,
            String firstName,
            String lastName,
            LocalDate birthDate,
            Gender gender,
            Relationship relationship) {}
}
