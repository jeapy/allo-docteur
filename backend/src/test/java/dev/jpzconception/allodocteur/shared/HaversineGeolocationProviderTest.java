package dev.jpzconception.allodocteur.shared;

import static org.assertj.core.api.Assertions.assertThat;

import dev.jpzconception.allodocteur.shared.geo.HaversineGeolocationProvider;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class HaversineGeolocationProviderTest {

    private final HaversineGeolocationProvider geo = new HaversineGeolocationProvider();

    private static BigDecimal d(String v) {
        return new BigDecimal(v);
    }

    @Test
    void oneDegreeOfLatitudeIsAbout111Km() {
        assertThat(geo.distanceKm(d("0"), d("0"), d("1"), d("0"))).isBetween(111.0, 111.4);
    }

    @Test
    void distanceIsZeroForTheSamePointAndSymmetric() {
        assertThat(geo.distanceKm(d("5.36"), d("-4.0083"), d("5.36"), d("-4.0083"))).isZero();
        double ab = geo.distanceKm(d("5.36"), d("-4.0083"), d("6.8276"), d("-5.2893"));
        double ba = geo.distanceKm(d("6.8276"), d("-5.2893"), d("5.36"), d("-4.0083"));
        assertThat(ab).isEqualTo(ba);
        assertThat(ab).isBetween(200.0, 230.0); // Abidjan – Yamoussoukro, à vol d'oiseau
    }

    @Test
    void doesNotResolveAddressesByDefault() {
        assertThat(geo.reverseGeocode(d("5.36"), d("-4.0083"))).isEmpty();
    }
}
