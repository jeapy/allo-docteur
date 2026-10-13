package dev.jpzconception.allodocteur.prescription.application;

import dev.jpzconception.allodocteur.prescription.domain.Prescription;
import dev.jpzconception.allodocteur.prescription.domain.PrescriptionItem;
import dev.jpzconception.allodocteur.prescription.domain.PrescriptionStatus;
import dev.jpzconception.allodocteur.prescription.infrastructure.PrescriptionItemRepository;
import dev.jpzconception.allodocteur.prescription.infrastructure.PrescriptionRepository;
import dev.jpzconception.allodocteur.shared.error.ApiException;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Façade pour les autres modules (pharmacie) : une ordonnance que son titulaire peut transmettre. */
@Service
@Transactional(readOnly = true)
public class PrescriptionAccess {

    public record Line(int lineNumber, String medicationName, String dosage, String frequency, Integer quantity,
                       String instructions) {}

    public record Orderable(UUID id, UUID recordId, UUID accountId, UUID dependentId, String beneficiaryName,
                            List<Line> lines) {}

    private final PrescriptionRepository prescriptions;
    private final PrescriptionItemRepository items;
    private final Clock clock;

    public PrescriptionAccess(PrescriptionRepository prescriptions, PrescriptionItemRepository items, Clock clock) {
        this.prescriptions = prescriptions;
        this.items = items;
        this.clock = clock;
    }

    /**
     * Ordonnance du compte, valide (émise, non annulée, non expirée). 404 si elle appartient à un autre compte :
     * l'existence n'est pas révélée.
     */
    public Orderable requireOrderable(UUID accountId, UUID prescriptionId) {
        Prescription p = prescriptions.findById(prescriptionId)
                .filter(x -> x.getAccountId().equals(accountId))
                .orElseThrow(() -> ApiException.notFound("PRESCRIPTION_NOT_FOUND", "Ordonnance introuvable."));
        if (p.getStatus() != PrescriptionStatus.ISSUED || p.isExpired(Instant.now(clock))) {
            throw ApiException.conflict("PRESCRIPTION_NOT_USABLE", "Cette ordonnance est annulée ou expirée.");
        }
        List<Line> lines = items.findByPrescriptionIdOrderByLineNumber(prescriptionId).stream()
                .map(PrescriptionAccess::toLine).toList();
        return new Orderable(p.getId(), p.getRecordId(), p.getAccountId(), p.getDependentId(), p.getBeneficiaryName(), lines);
    }

    private static Line toLine(PrescriptionItem i) {
        return new Line(i.getLineNumber(), i.getMedicationName(), i.getDosage(), i.getFrequency(), i.getQuantity(),
                i.getInstructions());
    }
}
