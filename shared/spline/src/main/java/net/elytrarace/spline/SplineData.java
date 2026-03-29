package net.elytrarace.spline;

import net.elytrarace.common.map.model.PortalDTO;
import org.apache.commons.geometry.euclidean.threed.Vector3D;

import java.util.Collection;
import java.util.List;

/**
 * Holds pre-computed spline data for a map. Caches the generated points
 * to avoid recalculation every render tick.
 *
 * <p>Immutable — create a new instance when portals change.</p>
 */
public final class SplineData {

    private final List<Vector3D> fullSpline;
    private final int portalCount;

    private SplineData(List<Vector3D> fullSpline, int portalCount) {
        this.fullSpline = fullSpline;
        this.portalCount = portalCount;
    }

    /**
     * Creates spline data from a set of portals.
     * Pre-computes the full spline with builder density for caching.
     */
    public static SplineData fromPortals(Collection<? extends PortalDTO> portals) {
        var fullSpline = SplineGenerator.generate(portals, SplineConfig.BUILDER);
        return new SplineData(fullSpline, portals.size());
    }

    /**
     * Returns the full spline (all points). Used for FULL visibility and builder preview.
     */
    public List<Vector3D> fullSpline() {
        return fullSpline;
    }

    /**
     * Returns a partial spline around the current portal. Used for PARTIAL visibility.
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
            case FULL -> fullSpline;
            case PARTIAL -> partialSpline(portals, currentPortal, config);
            case HIDDEN -> List.of();
        };
    }

    public int portalCount() {
        return portalCount;
    }

    public boolean isEmpty() {
        return fullSpline.isEmpty();
    }
}
