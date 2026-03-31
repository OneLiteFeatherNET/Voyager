package net.elytrarace.setup.preview;

import net.elytrarace.common.map.model.LocationDTO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Tests the interpolation math used by ParticleRenderer.
 * Duplicates the interpolate() logic to avoid loading Bukkit classes in tests.
 */
class ParticleRendererTest {

    /**
     * Same logic as ParticleRenderer.interpolate() — duplicated here to avoid
     * loading Bukkit Particle/Color classes which are compileOnly.
     */
    private static List<double[]> interpolate(LocationDTO from, LocationDTO to, int steps) {
        if (steps <= 1) {
            return List.of(new double[]{from.x() + 0.5, from.y() + 0.5, from.z() + 0.5});
        }
        var points = new ArrayList<double[]>(steps);
        for (int i = 0; i < steps; i++) {
            double t = (double) i / (steps - 1);
            points.add(new double[]{
                    from.x() + 0.5 + (to.x() - from.x()) * t,
                    from.y() + 0.5 + (to.y() - from.y()) * t,
                    from.z() + 0.5 + (to.z() - from.z()) * t
            });
        }
        return points;
    }

    @Test
    @DisplayName("Interpolate 5 points between (0,0,0) and (10,0,0)")
    void shouldInterpolateEvenlyOnXAxis() {
        var from = new LocationDTO(0, 0, 0, false);
        var to = new LocationDTO(10, 0, 0, false);

        var points = interpolate(from, to, 5);

        assertThat(points).hasSize(5);
        assertThat(points.get(0)[0]).isCloseTo(0.5, within(0.001));
        assertThat(points.get(4)[0]).isCloseTo(10.5, within(0.001));
        assertThat(points.get(2)[0]).isCloseTo(5.5, within(0.001));
        for (var point : points) {
            assertThat(point[1]).isCloseTo(0.5, within(0.001));
            assertThat(point[2]).isCloseTo(0.5, within(0.001));
        }
    }

    @Test
    @DisplayName("Interpolate diagonal line (0,0,0) to (10,20,30)")
    void shouldInterpolateDiagonally() {
        var from = new LocationDTO(0, 0, 0, false);
        var to = new LocationDTO(10, 20, 30, false);

        var points = interpolate(from, to, 3);

        assertThat(points).hasSize(3);
        assertThat(points.get(0)).containsExactly(0.5, 0.5, 0.5);
        assertThat(points.get(1)[0]).isCloseTo(5.5, within(0.001));
        assertThat(points.get(1)[1]).isCloseTo(10.5, within(0.001));
        assertThat(points.get(1)[2]).isCloseTo(15.5, within(0.001));
        assertThat(points.get(2)[0]).isCloseTo(10.5, within(0.001));
        assertThat(points.get(2)[1]).isCloseTo(20.5, within(0.001));
        assertThat(points.get(2)[2]).isCloseTo(30.5, within(0.001));
    }

    @Test
    @DisplayName("Interpolate with steps=1 returns single point at from")
    void shouldReturnSinglePointForStepsOne() {
        var from = new LocationDTO(5, 10, 15, false);
        var to = new LocationDTO(20, 30, 40, false);

        var points = interpolate(from, to, 1);

        assertThat(points).hasSize(1);
        assertThat(points.get(0)).containsExactly(5.5, 10.5, 15.5);
    }

    @Test
    @DisplayName("Interpolate same point returns that point repeated")
    void shouldHandleSamePoint() {
        var point = new LocationDTO(5, 5, 5, false);

        var points = interpolate(point, point, 4);

        assertThat(points).hasSize(4);
        for (var p : points) {
            assertThat(p[0]).isCloseTo(5.5, within(0.001));
            assertThat(p[1]).isCloseTo(5.5, within(0.001));
            assertThat(p[2]).isCloseTo(5.5, within(0.001));
        }
    }

    @Test
    @DisplayName("Interpolate with 2 steps returns start and end")
    void shouldReturnStartAndEndForTwoSteps() {
        var from = new LocationDTO(0, 0, 0, false);
        var to = new LocationDTO(100, 0, 0, false);

        var points = interpolate(from, to, 2);

        assertThat(points).hasSize(2);
        assertThat(points.get(0)[0]).isCloseTo(0.5, within(0.001));
        assertThat(points.get(1)[0]).isCloseTo(100.5, within(0.001));
    }
}
