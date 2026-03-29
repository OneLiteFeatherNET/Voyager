package net.elytrarace.spline;

import net.elytrarace.common.map.model.LocationDTO;
import net.elytrarace.common.map.model.PortalDTO;
import net.elytrarace.common.utils.SplineAPI;
import net.elytrarace.common.utils.WindowedStreamUtils;
import org.apache.commons.geometry.euclidean.threed.Vector3D;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;

/**
 * Generates centripetal Catmull-Rom splines (alpha=0.5) through ordered path points.
 * The curve passes exactly through every point — portals, guide points, and spawn.
 *
 * <p>Centripetal parameterization guarantees no cusps or self-intersections
 * (Yuksel, Wilson, Schaefer 2011).</p>
 *
 * <p>This is the only class that knows the spline algorithm. To swap Catmull-Rom
 * for a different algorithm, only this class needs to change.</p>
 */
public final class SplineGenerator {

    private static final double ALPHA = 0.5; // centripetal
    private static final double EPSILON = 1e-10;
    private static final double BLOCKS_PER_PARTICLE = 1.5;
    private static final int ARC_LENGTH_SAMPLES = 64;

    private SplineGenerator() {}

    /**
     * Compiles an ordered list of path points into spline segments.
     * Adds phantom endpoints for smooth start/end tangents.
     *
     * @param points ordered path points (spawn, guide, portal — sorted by orderIndex)
     * @return compiled segments, empty if fewer than 2 points
     */
    public static List<SplineSegment> compile(List<? extends PathPoint> points) {
        if (points.size() < 2) return List.of();

        var positions = points.stream().map(PathPoint::position).toList();

        // Phantom endpoints: mirror first/last point across the boundary
        var phantomStart = positions.getFirst().add(
                positions.getFirst().subtract(positions.get(1)));
        var phantomEnd = positions.getLast().add(
                positions.getLast().subtract(positions.get(positions.size() - 2)));

        var extended = new ArrayList<Vector3D>();
        extended.add(phantomStart);
        extended.addAll(positions);
        extended.add(phantomEnd);

        // Build segments: for each consecutive pair in the original points
        var segments = new ArrayList<SplineSegment>();
        for (int i = 1; i < extended.size() - 2; i++) {
            var p0 = extended.get(i - 1);
            var p1 = extended.get(i);
            var p2 = extended.get(i + 1);
            var p3 = extended.get(i + 2);

            double d01 = safeDistance(p0, p1);
            double d12 = safeDistance(p1, p2);
            double d23 = safeDistance(p2, p3);

            double t0 = 0.0;
            double t1 = t0 + Math.pow(d01, ALPHA);
            double t2 = t1 + Math.pow(d12, ALPHA);
            double t3 = t2 + Math.pow(d23, ALPHA);

            segments.add(new SplineSegment(p0, p1, p2, p3, t0, t1, t2, t3));
        }

        return segments;
    }

    /**
     * Samples the spline at approximately uniform arc-length intervals.
     * One particle every ~1.5 blocks for consistent visual density.
     *
     * @param segments compiled spline segments
     * @return sampled points for particle rendering
     */
    public static List<Vector3D> sampleUniform(List<SplineSegment> segments) {
        if (segments.isEmpty()) return List.of();

        // Estimate total arc length
        double totalLength = 0;
        double[] segLengths = new double[segments.size()];
        for (int i = 0; i < segments.size(); i++) {
            segLengths[i] = segments.get(i).estimateLength(ARC_LENGTH_SAMPLES);
            totalLength += segLengths[i];
        }

        int totalSamples = Math.max(2, (int) (totalLength / BLOCKS_PER_PARTICLE));
        var result = new ArrayList<Vector3D>(totalSamples);

        // Distribute samples proportional to segment arc length
        for (int i = 0; i < segments.size(); i++) {
            int segSamples = Math.max(2,
                    (int) Math.round(totalSamples * segLengths[i] / totalLength));
            for (int j = 0; j < segSamples; j++) {
                double t = (double) j / (segSamples - 1);
                result.add(segments.get(i).evaluate(t));
            }
        }

        return result;
    }

    // --- Convenience methods for backward compatibility ---

    /**
     * Generates spline from PathPoints (full pipeline: compile + sample).
     */
    public static List<Vector3D> generate(List<? extends PathPoint> points) {
        return sampleUniform(compile(points));
    }

    /**
     * Generates spline from portals only (legacy compatibility).
     * Extracts center points and converts to PathPoints.
     */
    public static List<Vector3D> generate(Collection<? extends PortalDTO> portals, SplineConfig config) {
        var pathPoints = portalsToPathPoints(portals);
        if (pathPoints.size() < 2) return List.of();

        if (config.visibility() == SplineVisibility.HIDDEN) return List.of();

        return sampleUniform(compile(pathPoints));
    }

    /**
     * Generates a partial spline around the current portal.
     */
    public static List<Vector3D> generatePartial(Collection<? extends PortalDTO> portals,
                                                  int currentPortal, SplineConfig config) {
        var allPoints = portalsToPathPoints(portals);
        if (allPoints.size() < 2) return List.of();

        int start = Math.max(0, currentPortal - 1);
        int end = Math.min(allPoints.size(), currentPortal + config.lookAhead() + 1);
        if (end - start < 2) return List.of();

        return sampleUniform(compile(allPoints.subList(start, end)));
    }

    /**
     * Generates spline from portals only (legacy, default config).
     */
    public static List<Vector3D> generate(Collection<? extends PortalDTO> portals) {
        return generate(portals, SplineConfig.EASY);
    }

    /**
     * Converts portals to PathPoints (portal centers only, no guide points).
     */
    public static List<PathPoint> portalsToPathPoints(Collection<? extends PortalDTO> portals) {
        var sorted = portals.stream()
                .sorted(Comparator.comparingInt(PortalDTO::index))
                .toList();

        var points = new ArrayList<PathPoint>();
        for (int i = 0; i < sorted.size(); i++) {
            var portal = sorted.get(i);
            var center = portal.locations().stream()
                    .filter(LocationDTO::center)
                    .findFirst()
                    .orElse(null);
            if (center == null) continue;

            var pos = Vector3D.of(center.x() + 0.5, center.y() + 0.5, center.z() + 0.5);
            points.add(new PathPoint.PortalPoint(pos, i * 100, portal.index()));
        }
        return points;
    }

    private static double safeDistance(Vector3D a, Vector3D b) {
        double d = a.distance(b);
        return d < EPSILON ? EPSILON : d;
    }
}
