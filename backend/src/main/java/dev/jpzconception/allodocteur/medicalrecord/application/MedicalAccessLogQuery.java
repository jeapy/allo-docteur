package dev.jpzconception.allodocteur.medicalrecord.application;

import dev.jpzconception.allodocteur.medicalrecord.domain.AccessOutcome;
import java.time.Instant;
import java.util.UUID;

/** Critères de recherche du journal d'accès (tous facultatifs). */
public record MedicalAccessLogQuery(UUID recordId, UUID actorId, AccessOutcome outcome, Instant from, Instant to) {}
