package dev.jpzconception.allodocteur.professional.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalTime;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Plage hebdomadaire récurrente. dayOfWeek : 1 = lundi … 7 = dimanche (ISO-8601). */
@Entity
@Table(name = "availability_rules")
@Getter
@Setter
@NoArgsConstructor
public class AvailabilityRule {

    @Id
    private UUID id = UUID.randomUUID();

    @Column(name = "professional_id", nullable = false)
    private UUID professionalId;

    @Column(name = "day_of_week", nullable = false)
    private int dayOfWeek;

    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;

    @Column(nullable = false)
    private boolean teleconsultation;

    @Column(name = "home_visit", nullable = false)
    private boolean homeVisit;
}
