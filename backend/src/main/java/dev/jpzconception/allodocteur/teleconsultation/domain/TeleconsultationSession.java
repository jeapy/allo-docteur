package dev.jpzconception.allodocteur.teleconsultation.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Salle audio/vidéo ouverte quand le professionnel démarre le rendez-vous. */
@Entity
@Table(name = "teleconsultation_sessions")
@Getter
@Setter
@NoArgsConstructor
public class TeleconsultationSession {

    @Id
    private UUID id = UUID.randomUUID();

    @Column(name = "appointment_id", nullable = false)
    private UUID appointmentId;

    @Column(nullable = false)
    private String provider;

    @Column(name = "room_id", nullable = false)
    private String roomId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MediaMode media;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "ended_at")
    private Instant endedAt;
}
