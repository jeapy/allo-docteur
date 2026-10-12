package dev.jpzconception.allodocteur.professional.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Indisponibilité ponctuelle ; startTime/endTime nuls = journée entière. */
@Entity
@Table(name = "availability_exceptions")
@Getter
@Setter
@NoArgsConstructor
public class AvailabilityException {

    @Id
    private UUID id = UUID.randomUUID();

    @Column(name = "professional_id", nullable = false)
    private UUID professionalId;

    @Column(name = "exception_date", nullable = false)
    private LocalDate date;

    @Column(name = "start_time")
    private LocalTime startTime;

    @Column(name = "end_time")
    private LocalTime endTime;

    private String reason;

    public boolean isFullDay() {
        return startTime == null;
    }
}
