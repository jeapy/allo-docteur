package dev.jpzconception.allodocteur.prescription.application;

import java.util.UUID;

public final class PrescriptionEvents {

    private PrescriptionEvents() {}

    public record Issued(UUID prescriptionId, UUID accountId, UUID professionalId) {}
}
