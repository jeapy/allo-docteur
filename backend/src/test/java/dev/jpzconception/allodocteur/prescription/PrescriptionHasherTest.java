package dev.jpzconception.allodocteur.prescription;

import static org.assertj.core.api.Assertions.assertThat;

import dev.jpzconception.allodocteur.prescription.domain.Prescription;
import dev.jpzconception.allodocteur.prescription.domain.PrescriptionHasher;
import dev.jpzconception.allodocteur.prescription.domain.PrescriptionItem;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PrescriptionHasherTest {

    private static Prescription prescription() {
        Prescription p = new Prescription();
        p.setNumber("ORD-2026-000001");
        p.setAppointmentId(UUID.fromString("00000000-0000-0000-0000-000000000001"));
        p.setRecordId(UUID.fromString("00000000-0000-0000-0000-000000000002"));
        p.setProfessionalId(UUID.fromString("00000000-0000-0000-0000-000000000003"));
        p.setBeneficiaryName("Awa Koné");
        p.setIssuedAt(Instant.parse("2026-10-01T10:00:00Z"));
        p.setValidUntil(Instant.parse("2026-12-30T10:00:00Z"));
        p.setNotes("À jeun");
        return p;
    }

    private static PrescriptionItem item(int line, String name, String dosage) {
        PrescriptionItem i = new PrescriptionItem();
        i.setLineNumber(line);
        i.setMedicationName(name);
        i.setDosage(dosage);
        return i;
    }

    @Test
    void hashIsStableAndIndependentOfListOrder() {
        Prescription p = prescription();
        String a = PrescriptionHasher.hash(p, List.of(item(1, "Paracétamol", "1 g"), item(2, "Amoxicilline", "500 mg")));
        String b = PrescriptionHasher.hash(p, List.of(item(2, "Amoxicilline", "500 mg"), item(1, "Paracétamol", "1 g")));
        assertThat(a).hasSize(64).isEqualTo(b);
    }

    @Test
    void anyChangeAltersTheHash() {
        Prescription p = prescription();
        String base = PrescriptionHasher.hash(p, List.of(item(1, "Paracétamol", "1 g")));
        assertThat(PrescriptionHasher.hash(p, List.of(item(1, "Paracétamol", "2 g")))).isNotEqualTo(base);
        p.setNotes("Autre");
        assertThat(PrescriptionHasher.hash(p, List.of(item(1, "Paracétamol", "1 g")))).isNotEqualTo(base);
    }

    @Test
    void fieldsCannotBeShiftedAcrossBoundaries() {
        Prescription p = prescription();
        String a = PrescriptionHasher.hash(p, List.of(item(1, "ab", "c")));
        String b = PrescriptionHasher.hash(p, List.of(item(1, "a", "bc")));
        assertThat(a).isNotEqualTo(b);
    }
}
