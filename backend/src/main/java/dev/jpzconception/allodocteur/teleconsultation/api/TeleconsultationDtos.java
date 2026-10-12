package dev.jpzconception.allodocteur.teleconsultation.api;

import dev.jpzconception.allodocteur.teleconsultation.domain.DocumentKind;
import dev.jpzconception.allodocteur.teleconsultation.domain.MediaMode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

public final class TeleconsultationDtos {

    private TeleconsultationDtos() {}

    public record SessionDto(UUID id, MediaMode media, String roomId, String joinUrl, String token, Instant tokenExpiresAt,
                             Instant startedAt) {}

    public record SendMessageRequest(@NotBlank @Size(max = 4000) String content) {}

    /** senderRole : PATIENT (le compte qui a réservé) ou PROFESSIONAL. */
    public record MessageDto(UUID id, UUID senderId, String senderRole, String content, Instant sentAt, Instant readAt) {}

    public record CountDto(long count) {}

    public record SharedDocumentDto(UUID id, UUID uploadedBy, DocumentKind kind, String filename, String caption,
                                    String contentType, long sizeBytes, Instant uploadedAt) {}
}
