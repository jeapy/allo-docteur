package dev.jpzconception.allodocteur.auth;

import static org.assertj.core.api.Assertions.assertThat;

import dev.jpzconception.allodocteur.auth.application.TotpService;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class TotpServiceTest {

    private final TotpService totp = new TotpService();

    // Secret ASCII "12345678901234567890" encodé en Base32 (vecteurs de test RFC 6238, SHA-1)
    private static final String RFC_SECRET = "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ";

    @Test
    void rfc6238Vector_time59() {
        assertThat(totp.code(RFC_SECRET, Instant.ofEpochSecond(59))).isEqualTo("287082");
    }

    @Test
    void rfc6238Vector_time1111111109() {
        assertThat(totp.code(RFC_SECRET, Instant.ofEpochSecond(1111111109L))).isEqualTo("081804");
    }

    @Test
    void verifyAcceptsAdjacentStepButNotFarOnes() {
        Instant t = Instant.ofEpochSecond(1_700_000_000L);
        String code = totp.code(RFC_SECRET, t);
        assertThat(totp.verify(RFC_SECRET, code, t.plusSeconds(30))).isTrue();
        assertThat(totp.verify(RFC_SECRET, code, t.plusSeconds(120))).isFalse();
        assertThat(totp.verify(RFC_SECRET, "abcdef", t)).isFalse();
        assertThat(totp.verify(RFC_SECRET, null, t)).isFalse();
    }

    @Test
    void generatedSecretIsBase32AndUsable() {
        String secret = totp.generateSecret();
        assertThat(secret).matches("[A-Z2-7]{32}");
        Instant now = Instant.now();
        assertThat(totp.verify(secret, totp.code(secret, now), now)).isTrue();
    }

    @Test
    void otpauthUriContainsIssuerAndSecret() {
        String uri = totp.otpauthUri("Allo Docteur", "a@b.c", "ABC");
        assertThat(uri).startsWith("otpauth://totp/Allo%20Docteur:a%40b.c").contains("secret=ABC");
    }
}
