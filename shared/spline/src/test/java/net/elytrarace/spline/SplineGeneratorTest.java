package net.elytrarace.spline;

import net.elytrarace.common.map.model.FilePortalDTO;
import net.elytrarace.common.map.model.LocationDTO;
import net.elytrarace.common.map.model.PortalDTO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import static org.assertj.core.api.Assertions.assertThat;

class SplineGeneratorTest {

    private static PortalDTO portalAt(int index, int x, int y, int z) {
        return new FilePortalDTO(index, List.of(
                new LocationDTO(x - 2, y, z, false),
                new LocationDTO(x + 2, y, z, false),
                new LocationDTO(x, y + 2, z, false),
                new LocationDTO(x, y - 2, z, false),
                new LocationDTO(x, y, z, true) // center
        ));
    }

    @Test
    @DisplayName("Returns empty for fewer than 4 portals")
    void shouldReturnEmptyForTooFewPortals() {
        Set<PortalDTO> portals = new TreeSet<>();
        portals.add(portalAt(1, 0, 50, 0));
        portals.add(portalAt(2, 10, 50, 0));
        portals.add(portalAt(3, 20, 50, 0));

        assertThat(SplineGenerator.generate(portals)).isEmpty();
    }

    @Test
    @DisplayName("Generates spline points for exactly 4 portals")
    void shouldGenerateForFourPortals() {
        Set<PortalDTO> portals = new TreeSet<>();
        portals.add(portalAt(1, 0, 50, 0));
        portals.add(portalAt(2, 20, 50, 0));
        portals.add(portalAt(3, 40, 50, 0));
        portals.add(portalAt(4, 60, 50, 0));

        var points = SplineGenerator.generate(portals);

        assertThat(points).isNotEmpty();
        // All Y values should be around 50.5 (block center)
        for (var point : points) {
            assertThat(point.getY()).isBetween(49.0, 52.0);
        }
    }

    @Test
    @DisplayName("Generates more points for 6+ portals")
    void shouldGenerateMorePointsForMorePortals() {
        Set<PortalDTO> portals4 = new TreeSet<>();
        portals4.add(portalAt(1, 0, 50, 0));
        portals4.add(portalAt(2, 20, 50, 0));
        portals4.add(portalAt(3, 40, 50, 0));
        portals4.add(portalAt(4, 60, 50, 0));

        Set<PortalDTO> portals6 = new TreeSet<>(portals4);
        portals6.add(portalAt(5, 80, 50, 0));
        portals6.add(portalAt(6, 100, 50, 0));

        var points4 = SplineGenerator.generate(portals4);
        var points6 = SplineGenerator.generate(portals6);

        assertThat(points6.size()).isGreaterThan(points4.size());
    }

    @Test
    @DisplayName("Partial spline only covers nearby portals")
    void shouldGeneratePartialSpline() {
        Set<PortalDTO> portals = new TreeSet<>();
        for (int i = 1; i <= 10; i++) {
            portals.add(portalAt(i, i * 20, 50, 0));
        }

        var full = SplineGenerator.generate(portals, SplineConfig.EASY);
        var partial = SplineGenerator.generatePartial(portals, 5, SplineConfig.MEDIUM);

        assertThat(full).isNotEmpty();
        assertThat(partial).isNotEmpty();
        assertThat(partial.size()).isLessThan(full.size());
    }

    @Test
    @DisplayName("HIDDEN config returns empty from SplineData")
    void shouldReturnEmptyForHidden() {
        Set<PortalDTO> portals = new TreeSet<>();
        for (int i = 1; i <= 5; i++) {
            portals.add(portalAt(i, i * 20, 50, 0));
        }

        var data = SplineData.fromPortals(portals);
        var visible = data.getVisibleSpline(portals, 2, SplineConfig.HARD);

        assertThat(visible).isEmpty();
    }

    @Test
    @DisplayName("SplineData caches full spline")
    void shouldCacheFullSpline() {
        Set<PortalDTO> portals = new TreeSet<>();
        for (int i = 1; i <= 5; i++) {
            portals.add(portalAt(i, i * 20, 50, 0));
        }

        var data = SplineData.fromPortals(portals);

        assertThat(data.isEmpty()).isFalse();
        assertThat(data.portalCount()).isEqualTo(5);
        assertThat(data.fullSpline()).isNotEmpty();
        // Calling twice returns same reference (cached)
        assertThat(data.fullSpline()).isSameAs(data.fullSpline());
    }
}
