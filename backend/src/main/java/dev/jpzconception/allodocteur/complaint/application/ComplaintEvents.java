package dev.jpzconception.allodocteur.complaint.application;

import dev.jpzconception.allodocteur.complaint.domain.ComplaintStatus;
import java.util.UUID;

public final class ComplaintEvents {

    private ComplaintEvents() {}

    /** Changement de statut ou réponse de l'administration, à signaler au déposant. */
    public record Updated(UUID complaintId, String number, UUID authorId, ComplaintStatus status, boolean reply) {}
}
