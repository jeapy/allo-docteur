package dev.jpzconception.allodocteur.shared.geo;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class NearestSearchTest {

    record Point(String name, BigDecimal lat, BigDecimal lon) {}

    private final GeolocationProvider geo = new HaversineGeolocationProvider();

    private static BigDecimal bd(double v) {
        return BigDecimal.valueOf(Math.round(v * 1_000_000) / 1_000_000.0);
    }

    private List<Point> randomPoints(long seed, int n, double lat0, double lon0, double spreadDeg) {
        Random r = new Random(seed);
        return IntStream.range(0, n)
                .mapToObj(i -> new Point("p%03d".formatted(i), bd(lat0 + (r.nextDouble() - 0.5) * spreadDeg),
                        bd(lon0 + (r.nextDouble() - 0.5) * spreadDeg)))
                .toList();
    }

    private List<NearestSearch.Ranked<Point>> run(List<Point> all, BigDecimal lat, BigDecimal lon, int limit,
                                                  int[] boxCalls) {
        return NearestSearch.nearest(lat, lon, limit, geo,
                box -> {
                    boxCalls[0]++;
                    return all.stream().filter(p -> p.lat().compareTo(box.minLat()) >= 0
                            && p.lat().compareTo(box.maxLat()) <= 0 && p.lon().compareTo(box.minLon()) >= 0
                            && p.lon().compareTo(box.maxLon()) <= 0).toList();
                },
                () -> all, Point::lat, Point::lon, Comparator.comparing(Point::name));
    }

    private List<String> bruteForce(List<Point> all, BigDecimal lat, BigDecimal lon, int limit) {
        return all.stream()
                .sorted(Comparator.comparingDouble((Point p) -> geo.distanceKm(lat, lon, p.lat(), p.lon()))
                        .thenComparing(Point::name))
                .limit(limit).map(Point::name).toList();
    }

    @Test
    void givesSameResultAsFullScanForManyRandomScenarios() {
        // Abidjan, Équateur, haute latitude : différents facteurs d'étirement en longitude.
        double[][] centers = {{5.35, -4.02}, {0.0, 20.0}, {60.0, 10.0}, {-33.9, 18.4}};
        for (double[] c : centers) {
            for (long seed = 1; seed <= 25; seed++) {
                List<Point> all = randomPoints(seed, 150, c[0], c[1], seed % 2 == 0 ? 1.5 : 30.0);
                BigDecimal lat = bd(c[0] + 0.1 * seed % 1), lon = bd(c[1] - 0.2 * seed % 1);
                for (int limit : new int[] {1, 3, 10, 200}) {
                    var got = run(all, lat, lon, limit, new int[1]).stream().map(r -> r.item().name()).toList();
                    assertThat(got).as("centre=%s seed=%d limit=%d", c[0], seed, limit)
                            .isEqualTo(bruteForce(all, lat, lon, limit));
                }
            }
        }
    }

    @Test
    void doesNotScanEverythingWhenNearbyItemsAreEnough() {
        List<Point> all = randomPoints(7, 60, 5.35, -4.02, 0.1); // tous à ~5 km
        int[] calls = {0};
        var got = run(all, bd(5.35), bd(-4.02), 5, calls);
        assertThat(got).hasSize(5);
        assertThat(calls[0]).isEqualTo(1);
    }

    @Test
    void reportsDistancesInAscendingOrder() {
        List<Point> all = randomPoints(3, 80, 5.35, -4.02, 5.0);
        var got = run(all, bd(5.35), bd(-4.02), 10, new int[1]);
        assertThat(got).extracting(NearestSearch.Ranked::km).isSorted();
    }

    @Test
    void fallsBackToFullListWhenBoxCrossesTheAntimeridian() {
        Point across = new Point("across", bd(0.0), bd(-179.9));
        Point near = new Point("near", bd(0.0), bd(179.5));
        Point far = new Point("far", bd(0.0), bd(150.0));
        var got = run(List.of(far, near, across), bd(0.0), bd(179.95), 2, new int[1]);
        assertThat(got).extracting(r -> r.item().name()).containsExactly("near", "across");
    }

    @Test
    void returnsFewerItemsWhenNotEnoughExist() {
        List<Point> all = randomPoints(9, 3, 5.35, -4.02, 1.0);
        assertThat(run(all, bd(5.35), bd(-4.02), 10, new int[1])).hasSize(3);
    }
}
