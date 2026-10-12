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

/** Image médicale ou document partagé pendant un rendez-vous. */
@Entity
@Table(name = "shared_documents")
@Getter
@Setter
@NoArgsConstructor
public class SharedDocument {

    @Id
    private UUID id = UUID.randomUUID();

    @Column(name = "appointment_id", nullable = false)
    private UUID appointmentId;

    @Column(name = "uploaded_by", nullable = false)
    private UUID uploadedBy;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DocumentKind kind;

    @Column(name = "original_filename", nullable = false)
    private String originalFilename;

    private String caption;

    @Column(name = "storage_key", nullable = false)
    private String storageKey;

    @Column(name = "content_type", nullable = false)
    private String contentType;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @Column(name = "checksum_sha256", nullable = false)
    private String checksumSha256;

    @Column(name = "uploaded_at", nullable = false)
    private Instant uploadedAt;
}
