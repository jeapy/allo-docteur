package dev.jpzconception.allodocteur.medicalrecord;

import static org.assertj.core.api.Assertions.assertThat;

import dev.jpzconception.allodocteur.medicalrecord.domain.AccessAction;
import dev.jpzconception.allodocteur.medicalrecord.domain.EntryKind;
import org.junit.jupiter.api.Test;

class EntryKindTest {

    @Test
    void clinicalKindsAreReservedToProfessionals() {
        assertThat(EntryKind.DIAGNOSIS.allowedForProfessional()).isTrue();
        assertThat(EntryKind.DIAGNOSIS.allowedForPatient()).isFalse();
        assertThat(EntryKind.CONSULTATION_NOTE.allowedForPatient()).isFalse();
    }

    @Test
    void personalNotesAreReservedToPatients() {
        assertThat(EntryKind.PERSONAL_NOTE.allowedForPatient()).isTrue();
        assertThat(EntryKind.PERSONAL_NOTE.allowedForProfessional()).isFalse();
    }

    @Test
    void everyKindHasAtLeastOneAuthor() {
        for (EntryKind k : EntryKind.values()) {
            assertThat(k.allowedForPatient() || k.allowedForProfessional()).as(k.name()).isTrue();
        }
    }

    @Test
    void writesAreFlaggedForAlwaysLogging() {
        assertThat(AccessAction.CREATE_ENTRY.isWrite()).isTrue();
        assertThat(AccessAction.CREATE_PRESCRIPTION.isWrite()).isTrue();
        assertThat(AccessAction.VIEW_RECORD.isWrite()).isFalse();
    }
}
