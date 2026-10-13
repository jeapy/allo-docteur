package dev.jpzconception.allodocteur.review.domain;

import dev.jpzconception.allodocteur.shared.persistence.AuditableEntity;
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

/** Avis d'un patient sur une consultation terminée (un seul par rendez-vous). */
@Entity
@Table(name = "reviews")
@Getter
@Setter
@NoArgsConstructor
public class Review extends AuditableEntity {

    @Id
    private UUID id = UUID.randomUUID();

    @Column(name = "appointment_id", nullable = false, updatable = false)
    private UUID appointmentId;

    @Column(name = "professional_id", nullable = false, updatable = false)
    private UUID professionalId;

    @Column(name = "account_id", nullable = false, updatable = false)
    private UUID accountId;

    /** « Prénom N. » : le nom complet du patient n'est jamais exposé. */
    @Column(name = "author_name", nullable = false, updatable = false)
    private String authorName;

    @Column(nullable = false, updatable = false)
    private int rating;

    @Column(length = 2000)
    private String comment;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ReviewStatus status = ReviewStatus.VISIBLE;

    @Column(name = "hidden_reason")
    private String hiddenReason;

    @Column(name = "hidden_by")
    private UUID hiddenBy;

    @Column(name = "hidden_at")
    private Instant hiddenAt;
}
