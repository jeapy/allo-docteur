package dev.jpzconception.allodocteur.medicalrecord.domain;

import dev.jpzconception.allodocteur.shared.crypto.EncryptedStringConverter;
import dev.jpzconception.allodocteur.user.domain.Role;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
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

@Entity
@Table(name = "medical_record_entries")
@SQLRestriction("deleted_at is null")
@Getter
@Setter
@NoArgsConstructor
public class MedicalRecordEntry {

    @Id
    private UUID id = UUID.randomUUID();

    @Column(name = "record_id", nullable = false)
    private UUID recordId;

    @Column(name = "appointment_id")
    private UUID appointmentId;

    @Column(name = "author_id", nullable = false)
    private UUID authorId;

    @Enumerated(EnumType.STRING)
    @Column(name = "author_role", nullable = false)
    private Role authorRole;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EntryKind kind;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(nullable = false, columnDefinition = "text")
    private String title;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(columnDefinition = "text")
    private String content;

    @Column(name = "occurred_on", nullable = false)
    private LocalDate occurredOn;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;
}
