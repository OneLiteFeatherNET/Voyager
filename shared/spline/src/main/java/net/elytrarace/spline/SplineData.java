package net.elytrarace.spline;

import net.elytrarace.common.map.model.PortalDTO;
import org.apache.commons.geometry.euclidean.threed.Vector3D;

import java.util.Collection;
import java.util.List;

/**
 * Holds pre-computed spline data for a map. Caches the generated points
 * to avoid recalculation every render tick.
 *
 * <p>Immutable — create a new instance when points change.</p>
 */
public final class SplineData {

    private final List<SplineSegment> segments;
    private final List<Vector3D> sampledPoints;
    private final int pointCount;

    private SplineData(List<SplineSegment> segments, List<Vector3D> sampledPoints, int pointCount) {
        this.segments = segments;
        this.sampledPoints = sampledPoints;
        this.pointCount = pointCount;
    }

    /**
     * Creates spline data from PathPoints (portals + guide points + spawn).
     */
    public static SplineData fromPathPoints(List<? extends PathPoint> points) {
        var segments = SplineGenerator.compile(points);
        var sampled = SplineGenerator.sampleUniform(segments);
        return new SplineData(segments, sampled, points.size());
    }

    /**
     * Creates spline data from portals only (no guide points).
     */
    public static SplineData fromPortals(Collection<? extends PortalDTO> portals) {
        var points = SplineGenerator.portalsToPathPoints(portals);
        return fromPathPoints(points);
    }

    /**
     * Returns the full sampled spline (all points). For FULL visibility and builder preview.
     */
    public List<Vector3D> fullSpline() {
        return sampledPoints;
    }

    /**
     * Returns a partial spline around the current portal. For PARTIAL visibility.
     */
    public List<Vector3D> partialSpline(Collection<? extends PortalDTO> portals,
                                        int currentPortal, SplineConfig config) {
        return SplineGenerator.generatePartial(portals, currentPortal, config);
    }

    /**
     * Returns the appropriate spline points based on the config's visibility mode.
     */
    public List<Vector3D> getVisibleSpline(Collection<? extends PortalDTO> portals,
                                           int currentPortal, SplineConfig config) {
        return switch (config.visibility()) {
            case FULL -> sampledPoints;
            case PARTIAL -> partialSpline(portals, currentPortal, config);
            case HIDDEN -> List.of();
        };
    }

    public List<SplineSegment> segments() {
        return segments;
    }

    public int pointCount() {
        return pointCount;
    }

    public boolean isEmpty() {
        return sampledPoints.isEmpty();
    }
}
