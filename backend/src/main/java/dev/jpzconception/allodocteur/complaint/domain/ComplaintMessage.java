package dev.jpzconception.allodocteur.complaint.domain;

import dev.jpzconception.allodocteur.shared.crypto.EncryptedStringConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Message du fil d'une réclamation (déposant ou administration). */
@Entity
@Table(name = "complaint_messages")
@Getter
@Setter
@NoArgsConstructor
public class ComplaintMessage {

    @Id
    private UUID id = UUID.randomUUID();

    @Column(name = "complaint_id", nullable = false, updatable = false)
    private UUID complaintId;

    @Column(name = "author_id", nullable = false, updatable = false)
    private UUID authorId;

    @Column(name = "from_admin", nullable = false, updatable = false)
    private boolean fromAdmin;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(nullable = false, updatable = false, columnDefinition = "text")
    private String body;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
