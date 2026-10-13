package dev.jpzconception.allodocteur.sos.application;

import dev.jpzconception.allodocteur.sos.domain.SosCategory;
import java.util.UUID;

/** Événements SOS : la couche de notification (phase 7) s'y abonnera pour prévenir les professionnels et le patient. */
public final class SosEvents {

    private SosEvents() {}

    public record Created(UUID alertId, UUID accountId, Integer cityId, SosCategory category) {}

    public record Accepted(UUID alertId, UUID accountId, UUID professionalId, UUID appointmentId) {}

    public record Resolved(UUID alertId, UUID accountId, UUID professionalId) {}

    public record Cancelled(UUID alertId, UUID accountId, UUID cancelledBy) {}
}
