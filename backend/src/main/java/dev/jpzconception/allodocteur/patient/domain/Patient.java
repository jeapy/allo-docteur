package dev.jpzconception.allodocteur.patient.domain;

import dev.jpzconception.allodocteur.reference.domain.City;
import dev.jpzconception.allodocteur.reference.domain.District;
import dev.jpzconception.allodocteur.shared.persistence.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Profil patient : l'identifiant est celui du compte utilisateur. */
@Entity
@Table(name = "patients")
@Getter
@Setter
@NoArgsConstructor
public class Patient extends AuditableEntity {

    @Id
    @Column(name = "user_id")
    private UUID id;

    @Column(name = "birth_date")
    private LocalDate birthDate;

    @Enumerated(EnumType.STRING)
    private Gender gender;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "city_id")
    private City city;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "district_id")
    private District district;

    private String address;

    @Column(name = "emergency_contact_name")
    private String emergencyContactName;

    @Column(name = "emergency_contact_phone")
    private String emergencyContactPhone;

    @Column(nullable = false)
    private boolean premium;

    public Patient(UUID id) {
        this.id = id;
    }
}
