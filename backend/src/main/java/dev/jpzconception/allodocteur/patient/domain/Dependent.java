package dev.jpzconception.allodocteur.patient.domain;

import dev.jpzconception.allodocteur.shared.persistence.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.SQLRestriction;

/** Personne à charge (enfant, conjoint, parent…) gérée par le compte du tuteur. */
@Entity
@Table(name = "dependents")
@SQLRestriction("deleted_at is null")
@Getter
@Setter
@NoArgsConstructor
public class Dependent extends AuditableEntity {

    @Id
    private UUID id = UUID.randomUUID();

    @Column(name = "guardian_id", nullable = false)
    private UUID guardianId;

    @Column(name = "first_name", nullable = false)
    private String firstName;

    @Column(name = "last_name", nullable = false)
    private String lastName;

    @Column(name = "birth_date", nullable = false)
    private LocalDate birthDate;

    @Enumerated(EnumType.STRING)
    private Gender gender;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Relationship relationship;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    public String fullName() {
        return firstName + " " + lastName;
    }
}
