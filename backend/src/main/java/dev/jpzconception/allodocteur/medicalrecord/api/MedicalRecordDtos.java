package dev.jpzconception.allodocteur.medicalrecord.api;

import dev.jpzconception.allodocteur.medicalrecord.domain.AccessAction;
import dev.jpzconception.allodocteur.medicalrecord.domain.AccessOutcome;
import dev.jpzconception.allodocteur.medicalrecord.domain.BloodGroup;
import dev.jpzconception.allodocteur.medicalrecord.domain.EntryKind;
import dev.jpzconception.allodocteur.user.domain.Role;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public final class MedicalRecordDtos {

    private MedicalRecordDtos() {}

    // ------------------------------------------------------------ consentement

    public record AcceptConsentRequest(@NotBlank String version) {}

    /** La version courante du texte, et l'état du consentement du demandeur. */
    public record ConsentStatusDto(String version, String title, String content, Instant publishedAt,
                                   boolean accepted, Instant acceptedAt) {}

    public record ConsentHistoryDto(String version, Instant grantedAt, Instant revokedAt) {}

    // ------------------------------------------------------------ dossier

    public record SummaryRequest(
            BloodGroup bloodGroup,
            @Size(max = 4000) String allergies,
            @Size(max = 4000) String chronicConditions,
            @Size(max = 4000) String currentMedications,
            @Size(max = 4000) String familyHistory) {}

    public record RecordDto(
            UUID id,
            UUID accountId,
            UUID dependentId,
            String beneficiaryName,
            BloodGroup bloodGroup,
            String allergies,
            String chronicConditions,
            String currentMedications,
            String familyHistory,
            Instant updatedAt) {}

    public record EntryRequest(
            @NotNull EntryKind kind,
            @NotBlank @Size(max = 200) String title,
            @Size(max = 8000) String content,
            /** Date de l'événement ; aujourd'hui par défaut. */
            @PastOrPresent LocalDate occurredOn,
            /** Rendez-vous à l'origine de l'entrée (facultatif) ; doit concerner la même personne. */
            UUID appointmentId) {}

    public record EntryDto(
            UUID id,
            UUID recordId,
            UUID appointmentId,
            UUID authorId,
            String authorName,
            Role authorRole,
            EntryKind kind,
            String title,
            String content,
            LocalDate occurredOn,
            Instant createdAt,
            /** Vrai si l'utilisateur courant peut retirer cette entrée (son auteur patient). */
            boolean removable) {}

    // ------------------------------------------------------------ journal d'accès

    public record AccessLogDto(
            UUID id,
            UUID recordId,
            UUID actorId,
            String actorName,
            Role actorRole,
            AccessAction action,
            UUID targetId,
            AccessOutcome outcome,
            String reason,
            /** Réservée à l'administration (SUPER). */
            String ipAddress,
            Instant occurredAt) {}
}
