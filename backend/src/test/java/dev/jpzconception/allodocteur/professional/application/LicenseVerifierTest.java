package dev.jpzconception.allodocteur.professional.application;

import static org.assertj.core.api.Assertions.assertThat;

import dev.jpzconception.allodocteur.professional.application.LicenseRegistry.Answer;
import dev.jpzconception.allodocteur.professional.application.LicenseVerifier.Result;
import dev.jpzconception.allodocteur.professional.domain.LicenseCheckStatus;
import dev.jpzconception.allodocteur.reference.domain.ProfessionalType;
import java.util.Map;
import org.junit.jupiter.api.Test;

class LicenseVerifierTest {

    private static final String DEFAULT = "[A-Za-z0-9][A-Za-z0-9 ./_-]{3,49}";

    private static ProfessionalType type(String code) {
        ProfessionalType t = new ProfessionalType();
        t.setCode(code);
        return t;
    }

    private static LicenseVerifier verifier(Answer answer, Map<String, String> patterns) {
        return new LicenseVerifier((typeCode, number) -> answer,
                new ProfessionalProperties(false, DEFAULT, patterns));
    }

    @Test
    void normalizesWhitespace() {
        assertThat(LicenseVerifier.normalize("  CI   1234 ")).isEqualTo("CI 1234");
        assertThat(LicenseVerifier.normalize(null)).isEmpty();
    }

    @Test
    void defaultPatternAppliesWhenNoRegistryIsAvailable() {
        var v = verifier(Answer.UNAVAILABLE, Map.of());
        assertThat(v.check(type("MEDECIN"), "CI-2024-0042")).isEqualTo(Result.FORMAT_VALID);
        assertThat(v.check(type("MEDECIN"), "!!")).isEqualTo(Result.INVALID_FORMAT);
        assertThat(v.check(type("MEDECIN"), "")).isEqualTo(Result.INVALID_FORMAT);
    }

    @Test
    void perTypePatternOverridesDefaultCaseInsensitivelyOnKey() {
        var v = verifier(Answer.UNAVAILABLE, Map.of("medecin", "MED-\\d{5}"));
        assertThat(v.check(type("MEDECIN"), "MED-12345")).isEqualTo(Result.FORMAT_VALID);
        assertThat(v.check(type("MEDECIN"), "CI-2024-0042")).isEqualTo(Result.INVALID_FORMAT);
        assertThat(v.check(type("INFIRMIER"), "CI-2024-0042")).isEqualTo(Result.FORMAT_VALID);
    }

    @Test
    void registryAnswerIsReflectedInStatus() {
        assertThat(verifier(Answer.FOUND, Map.of()).check(type("X"), "ABCD-1")).isEqualTo(Result.REGISTRY_CONFIRMED);
        assertThat(verifier(Answer.NOT_FOUND, Map.of()).check(type("X"), "ABCD-1")).isEqualTo(Result.REGISTRY_NOT_FOUND);
        assertThat(LicenseVerifier.statusOf(Result.REGISTRY_CONFIRMED)).isEqualTo(LicenseCheckStatus.CONFIRMED);
        assertThat(LicenseVerifier.statusOf(Result.REGISTRY_NOT_FOUND)).isEqualTo(LicenseCheckStatus.NOT_FOUND);
        assertThat(LicenseVerifier.statusOf(Result.FORMAT_VALID)).isEqualTo(LicenseCheckStatus.FORMAT_VALID);
    }
}
