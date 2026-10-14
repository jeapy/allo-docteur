package dev.jpzconception.allodocteur.shared.geo;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Recherche des {@code limit} éléments les plus proches d'une position sans charger toute la table.
 *
 * <p>On interroge d'abord un rectangle autour du point (index sur latitude/longitude) de rayon croissant. Le résultat
 * est <b>exact</b> : on ne s'arrête que si au moins {@code limit} éléments se trouvent à moins de {@code R} km, car tout
 * élément hors du rectangle est forcément à plus de {@code R} km et ne peut donc pas entrer dans le classement.
 * Si aucun rayon ne suffit, on retombe sur la lecture complète.
 */
public final class NearestSearch {

    /** Rayons essayés, en km. */
    static final double[] RADII_KM = {15, 50, 150};
    /** Longueur minimale d'un degré de latitude, en km (marge de sécurité : le rectangle est un peu plus large). */
    private static final double KM_PER_DEGREE = 110.0;

    private NearestSearch() {}

    public record Ranked<T>(T item, double km) {}

    public record BoundingBox(BigDecimal minLat, BigDecimal maxLat, BigDecimal minLon, BigDecimal maxLon) {

        public static BoundingBox around(BigDecimal latitude, BigDecimal longitude, double radiusKm) {
            double lat = latitude.doubleValue();
            double lon = longitude.doubleValue();
            double dLat = radiusKm / KM_PER_DEGREE;
            double cos = Math.max(Math.cos(Math.toRadians(Math.min(Math.abs(lat) + dLat, 89.0))), 0.01);
            double dLon = Math.min(radiusKm / (KM_PER_DEGREE * cos), 180.0);
            return new BoundingBox(bd(lat - dLat), bd(lat + dLat), bd(lon - dLon), bd(lon + dLon));
        }

        private static BigDecimal bd(double v) {
            return BigDecimal.valueOf(v);
        }
    }

    public static <T> List<Ranked<T>> nearest(BigDecimal latitude, BigDecimal longitude, int limit,
                                              GeolocationProvider geo,
                                              Function<BoundingBox, List<T>> inBox,
                                              Supplier<List<T>> all,
                                              Function<T, BigDecimal> latOf, Function<T, BigDecimal> lonOf,
                                              Comparator<T> tieBreak) {
        int wanted = Math.max(limit, 1);
        for (double radius : RADII_KM) {
            BoundingBox box = BoundingBox.around(latitude, longitude, radius);
            // Le rectangle déborde des limites du globe (pôles, antiméridien) : il ne serait plus exact.
            if (box.minLat().doubleValue() < -90 || box.maxLat().doubleValue() > 90
                    || box.minLon().doubleValue() < -180 || box.maxLon().doubleValue() > 180) {
                break;
            }
            List<Ranked<T>> candidates = rank(inBox.apply(box), latitude, longitude, geo, latOf, lonOf, tieBreak);
            long within = candidates.stream().filter(c -> c.km() <= radius).count();
            if (within >= wanted) return candidates.stream().limit(wanted).toList();
        }
        return rank(all.get(), latitude, longitude, geo, latOf, lonOf, tieBreak).stream().limit(wanted).toList();
    }

    private static <T> List<Ranked<T>> rank(List<T> items, BigDecimal latitude, BigDecimal longitude,
                                            GeolocationProvider geo, Function<T, BigDecimal> latOf,
                                            Function<T, BigDecimal> lonOf, Comparator<T> tieBreak) {
        return items.stream()
                .map(i -> new Ranked<>(i, geo.distanceKm(latitude, longitude, latOf.apply(i), lonOf.apply(i))))
                .sorted(Comparator.<Ranked<T>>comparingDouble(Ranked::km).thenComparing(Ranked::item, tieBreak))
                .toList();
    }
}
