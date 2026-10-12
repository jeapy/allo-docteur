package dev.jpzconception.allodocteur.teleconsultation.domain;

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

/** Message échangé dans le cadre d'un rendez-vous. Contenu chiffré au repos. */
@Entity
@Table(name = "messages")
@Getter
@Setter
@NoArgsConstructor
public class Message {

    @Id
    private UUID id = UUID.randomUUID();

    @Column(name = "appointment_id", nullable = false)
    private UUID appointmentId;

    @Column(name = "sender_id", nullable = false)
    private UUID senderId;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(nullable = false, columnDefinition = "text")
    private String content;

    @Column(name = "sent_at", nullable = false)
    private Instant sentAt;

    @Column(name = "read_at")
    private Instant readAt;
}
