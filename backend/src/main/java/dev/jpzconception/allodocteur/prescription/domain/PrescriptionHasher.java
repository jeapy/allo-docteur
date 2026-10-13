package dev.jpzconception.allodocteur.prescription.domain;

import dev.jpzconception.allodocteur.shared.storage.FileInspector;
import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.List;

/**
 * Empreinte SHA-256 du contenu clinique d'une ordonnance (numéro, rendez-vous, bénéficiaire, dates, lignes, notes).
 * Permet de détecter une altération en base ; ce n'est pas une signature électronique qualifiée.
 */
public final class PrescriptionHasher {

    private PrescriptionHasher() {}

    public static String hash(Prescription p, List<PrescriptionItem> items) {
        StringBuilder sb = new StringBuilder();
        field(sb, p.getNumber());
        field(sb, p.getAppointmentId());
        field(sb, p.getRecordId());
        field(sb, p.getProfessionalId());
        field(sb, p.getBeneficiaryName());
        field(sb, p.getIssuedAt() == null ? null : p.getIssuedAt().toEpochMilli());
        field(sb, p.getValidUntil() == null ? null : p.getValidUntil().toEpochMilli());
        field(sb, p.getNotes());
        items.stream().sorted(Comparator.comparingInt(PrescriptionItem::getLineNumber)).forEach(i -> {
            field(sb, i.getLineNumber());
            field(sb, i.getMedicationName());
            field(sb, i.getDosage());
            field(sb, i.getFrequency());
            field(sb, i.getDurationDays());
            field(sb, i.getQuantity());
            field(sb, i.getInstructions());
        });
        return FileInspector.sha256(sb.toString().getBytes(StandardCharsets.UTF_8));
    }

    /** Champ préfixé par sa longueur : évite les collisions par concaténation. */
    private static void field(StringBuilder sb, Object value) {
        String s = value == null ? "\u0000" : value.toString();
        sb.append(s.length()).append(':').append(s).append('|');
    }
}
