package dev.jpzconception.allodocteur.prescription;

import static org.assertj.core.api.Assertions.assertThat;

import dev.jpzconception.allodocteur.prescription.application.PrescriptionProperties;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class PrescriptionPropertiesTest {

    @Test
    void defaultsAreSensible() {
        PrescriptionProperties p = new PrescriptionProperties(90, 3, java.util.List.of("GENERAL_PRACTITIONER"));
        assertThat(p.validityDays()).isEqualTo(90);
        assertThat(p.allowedProfessionalTypes()).containsExactly("GENERAL_PRACTITIONER");
    }

    @Test
    void numberIsSequentialAndPaddedPerYear() throws Exception {
        var m = Class.forName("dev.jpzconception.allodocteur.prescription.application.PrescriptionService")
                .getDeclaredMethod("formatNumber", Instant.class, long.class);
        m.setAccessible(true);
        assertThat(m.invoke(null, Instant.parse("2026-10-13T08:00:00Z"), 42L)).isEqualTo("ORD-2026-000042");
    }
}
