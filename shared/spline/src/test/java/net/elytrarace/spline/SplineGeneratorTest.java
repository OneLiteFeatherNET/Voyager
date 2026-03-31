package net.elytrarace.spline;

import net.elytrarace.common.map.model.FilePortalDTO;
import net.elytrarace.common.map.model.LocationDTO;
import net.elytrarace.common.map.model.PortalDTO;
import org.apache.commons.geometry.euclidean.threed.Vector3D;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
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
                new LocationDTO(x, y, z, true)
        ));
    }

    @Test
    @DisplayName("Returns empty for only 1 portal (need at least 2)")
    void shouldReturnEmptyForSinglePortal() {
        Set<PortalDTO> portals = new TreeSet<>();
        portals.add(portalAt(1, 0, 50, 0));

        assertThat(SplineGenerator.generate(portals)).isEmpty();
    }

    @Test
    @DisplayName("Generates spline for 2-3 portals (phantom endpoints)")
    void shouldGenerateForTwoOrThreePortals() {
        Set<PortalDTO> portals = new TreeSet<>();
        portals.add(portalAt(1, 0, 50, 0));
        portals.add(portalAt(2, 30, 50, 0));
        portals.add(portalAt(3, 60, 50, 0));

        assertThat(SplineGenerator.generate(portals)).isNotEmpty();
    }

    @Test
    @DisplayName("Generates spline for 4+ portals (legacy API)")
    void shouldGenerateForFourPortals() {
        Set<PortalDTO> portals = new TreeSet<>();
        portals.add(portalAt(1, 0, 50, 0));
        portals.add(portalAt(2, 20, 50, 0));
        portals.add(portalAt(3, 40, 50, 0));
        portals.add(portalAt(4, 60, 50, 0));

        var points = SplineGenerator.generate(portals);
        assertThat(points).isNotEmpty();
    }

    @Test
    @DisplayName("PathPoint-based generation works with 2+ points")
    void shouldGenerateFromPathPoints() {
        var points = List.<PathPoint>of(
                new PathPoint.SpawnPoint(Vector3D.of(0, 50, 0), 0),
                new PathPoint.PortalPoint(Vector3D.of(20, 50, 0), 100, 1),
                new PathPoint.PortalPoint(Vector3D.of(40, 55, 0), 200, 2),
                new PathPoint.PortalPoint(Vector3D.of(60, 50, 0), 300, 3)
        );

        var result = SplineGenerator.generate(points);
        assertThat(result).isNotEmpty();
    }

    @Test
    @DisplayName("Guide points between portals affect the curve")
    void shouldCurveAroundGuidePoint() {
        // Straight line portals
        var withoutGuide = List.<PathPoint>of(
                new PathPoint.PortalPoint(Vector3D.of(0, 50, 0), 100, 1),
                new PathPoint.PortalPoint(Vector3D.of(40, 50, 0), 200, 2),
                new PathPoint.PortalPoint(Vector3D.of(80, 50, 0), 300, 3),
                new PathPoint.PortalPoint(Vector3D.of(120, 50, 0), 400, 4)
        );

        // Same portals but with a guide point pulling the curve to Y=80
        var withGuide = List.<PathPoint>of(
                new PathPoint.PortalPoint(Vector3D.of(0, 50, 0), 100, 1),
                new PathPoint.GuidePoint(Vector3D.of(20, 80, 0), 150),
                new PathPoint.PortalPoint(Vector3D.of(40, 50, 0), 200, 2),
                new PathPoint.PortalPoint(Vector3D.of(80, 50, 0), 300, 3),
                new PathPoint.PortalPoint(Vector3D.of(120, 50, 0), 400, 4)
        );

        var pointsWithout = SplineGenerator.generate(withoutGuide);
        var pointsWith = SplineGenerator.generate(withGuide);

        // The guided curve should have higher Y values in the first segment
        double maxYWithout = pointsWithout.stream().mapToDouble(Vector3D::getY).max().orElse(0);
        double maxYWith = pointsWith.stream().mapToDouble(Vector3D::getY).max().orElse(0);

        assertThat(maxYWith).isGreaterThan(maxYWithout);
    }

    @Test
    @DisplayName("Only 2 PathPoints produces a valid spline (linear)")
    void shouldHandleTwoPoints() {
        var points = List.<PathPoint>of(
                new PathPoint.SpawnPoint(Vector3D.of(0, 50, 0), 0),
                new PathPoint.PortalPoint(Vector3D.of(100, 50, 0), 100, 1)
        );

        var result = SplineGenerator.generate(points);
        assertThat(result).isNotEmpty();
    }

    @Test
    @DisplayName("SplineData.fromPortals works with 4+ portals")
    void shouldCreateSplineData() {
        Set<PortalDTO> portals = new TreeSet<>();
        for (int i = 1; i <= 5; i++) {
            portals.add(portalAt(i, i * 20, 50, 0));
        }

        var data = SplineData.fromPortals(portals);
        assertThat(data.isEmpty()).isFalse();
        assertThat(data.pointCount()).isEqualTo(5);
        assertThat(data.fullSpline()).isNotEmpty();
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
    @DisplayName("SplineSegment evaluates correctly at endpoints")
    void shouldEvaluateSegmentEndpoints() {
        var points = List.<PathPoint>of(
                new PathPoint.PortalPoint(Vector3D.of(0, 50, 0), 100, 1),
                new PathPoint.PortalPoint(Vector3D.of(20, 50, 0), 200, 2),
                new PathPoint.PortalPoint(Vector3D.of(40, 50, 0), 300, 3),
                new PathPoint.PortalPoint(Vector3D.of(60, 50, 0), 400, 4)
        );

        var segments = SplineGenerator.compile(points);
        assertThat(segments).isNotEmpty();

        // First segment: evaluate at t=0 should be near first point
        var start = segments.getFirst().evaluate(0.0);
        assertThat(start.getX()).isCloseTo(0.0, org.assertj.core.data.Offset.offset(0.5));
    }
}
