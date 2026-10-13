package dev.jpzconception.allodocteur.shared.geo;

import java.math.BigDecimal;
import java.util.Optional;
import org.springframework.stereotype.Component;

/** Distances par la formule de Haversine ; pas de géocodage inverse (à brancher sur un vrai fournisseur plus tard). */
@Component
public class HaversineGeolocationProvider implements GeolocationProvider {

    private static final double EARTH_RADIUS_KM = 6371.0088;

    @Override
    public double distanceKm(BigDecimal lat1, BigDecimal lon1, BigDecimal lat2, BigDecimal lon2) {
        double phi1 = Math.toRadians(lat1.doubleValue());
        double phi2 = Math.toRadians(lat2.doubleValue());
        double dPhi = phi2 - phi1;
        double dLambda = Math.toRadians(lon2.doubleValue() - lon1.doubleValue());
        double a = Math.sin(dPhi / 2) * Math.sin(dPhi / 2)
                + Math.cos(phi1) * Math.cos(phi2) * Math.sin(dLambda / 2) * Math.sin(dLambda / 2);
        return 2 * EARTH_RADIUS_KM * Math.asin(Math.min(1.0, Math.sqrt(a)));
    }

    @Override
    public Optional<String> reverseGeocode(BigDecimal latitude, BigDecimal longitude) {
        return Optional.empty();
    }
}
