package dev.jpzconception.allodocteur.shared;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.jpzconception.allodocteur.shared.config.AppProperties;
import dev.jpzconception.allodocteur.shared.crypto.AesGcmCrypto;
import java.util.Base64;
import org.junit.jupiter.api.Test;

class AesGcmCryptoTest {

    private static AesGcmCrypto crypto(byte fill) {
        byte[] key = new byte[32];
        java.util.Arrays.fill(key, fill);
        return new AesGcmCrypto(new AppProperties(null, null,
                new AppProperties.Crypto(Base64.getEncoder().encodeToString(key)), null, null, null, null));
    }

    @Test
    void roundTrip() {
        AesGcmCrypto c = crypto((byte) 1);
        String enc = c.encrypt("allergie: pénicilline");
        assertThat(enc).startsWith("v1:").doesNotContain("pénicilline");
        assertThat(c.decrypt(enc)).isEqualTo("allergie: pénicilline");
    }

    @Test
    void encryptionIsRandomised() {
        AesGcmCrypto c = crypto((byte) 1);
        assertThat(c.encrypt("x")).isNotEqualTo(c.encrypt("x"));
    }

    @Test
    void wrongKeyFails() {
        String enc = crypto((byte) 1).encrypt("secret");
        assertThatThrownBy(() -> crypto((byte) 2).decrypt(enc)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void rejectsShortKey() {
        assertThatThrownBy(() -> new AesGcmCrypto(new AppProperties(null, null,
                new AppProperties.Crypto(Base64.getEncoder().encodeToString(new byte[16])), null, null, null, null)))
                .isInstanceOf(IllegalStateException.class);
    }
}
