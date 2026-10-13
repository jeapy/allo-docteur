package dev.jpzconception.allodocteur.shared.geo;

import java.math.BigDecimal;
import java.util.Optional;

/** Port de géolocalisation. L'implémentation par défaut calcule les distances sans appeler de service externe. */
public interface GeolocationProvider {

    /** Distance à vol d'oiseau, en kilomètres. */
    double distanceKm(BigDecimal lat1, BigDecimal lon1, BigDecimal lat2, BigDecimal lon2);

    /** Adresse lisible d'une position, si le fournisseur sait la résoudre. */
    Optional<String> reverseGeocode(BigDecimal latitude, BigDecimal longitude);
}
