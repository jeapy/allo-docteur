package dev.jpzconception.allodocteur.professional.domain;

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

@Entity
@Table(name = "professional_documents")
@Getter
@Setter
@NoArgsConstructor
public class ProfessionalDocument {

    @Id
    private UUID id = UUID.randomUUID();

    @Column(name = "professional_id", nullable = false)
    private UUID professionalId;

    @Enumerated(EnumType.STRING)
    @Column(name = "doc_type", nullable = false)
    private DocumentType type;

    @Column(name = "original_filename", nullable = false)
    private String originalFilename;

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
