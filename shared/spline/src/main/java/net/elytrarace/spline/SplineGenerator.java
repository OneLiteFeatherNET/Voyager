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
 * Generates Catmull-Rom spline curves through portal centers.
 * Platform-agnostic — used by game server (Minestom), setup plugin (Paper),
 * and any future platform.
 *
 * <p>The spline serves as a visual ideal racing line, similar to the driving
 * line in Need for Speed Shift. It shows players the optimal flight path
 * through the portals.</p>
 */
public final class SplineGenerator {

    private static final int BASE_POINTS_PER_SEGMENT = 10;
    private static final double DISTANCE_FACTOR = 0.001;

    private SplineGenerator() {}

    /**
     * Generates a full spline path through all portal centers.
     *
     * @param portals the portals (uses center locations, sorted by index)
     * @param config  spline configuration (density affects interpolation resolution)
     * @return all interpolated spline points, or empty list if fewer than 4 portals
     */
    public static List<Vector3D> generate(Collection<? extends PortalDTO> portals, SplineConfig config) {
        var centers = extractSortedCenters(portals);
        if (centers.size() < 4) {
            return List.of();
        }
        return interpolateAll(centers, config.density());
    }

    /**
     * Generates a partial spline path — only the segment around the current portal.
     * Used for {@link SplineVisibility#PARTIAL} mode.
     *
     * @param portals        all portals in the map
     * @param currentPortal  index of the portal the player is approaching (0-based)
     * @param config         spline configuration (lookAhead determines range)
     * @return spline points for the visible segment, or empty list
     */
    public static List<Vector3D> generatePartial(Collection<? extends PortalDTO> portals,
                                                  int currentPortal, SplineConfig config) {
        var sorted = portals.stream()
                .sorted(Comparator.comparingInt(PortalDTO::index))
                .toList();

        // Range: from 1 portal behind to lookAhead portals ahead
        int start = Math.max(0, currentPortal - 1);
        int end = Math.min(sorted.size(), currentPortal + config.lookAhead() + 1);
        if (end - start < 4) {
            // Not enough portals in range — expand to minimum 4
            start = Math.max(0, end - 4);
            end = Math.min(sorted.size(), start + 4);
        }
        if (end - start < 4) return List.of();

        var subset = sorted.subList(start, end);
        var centers = subset.stream()
                .map(portal -> portal.locations().stream()
                        .filter(LocationDTO::center)
                        .findFirst()
                        .orElse(null))
                .filter(loc -> loc != null)
                .map(loc -> Vector3D.of(loc.x() + 0.5, loc.y() + 0.5, loc.z() + 0.5))
                .toList();

        if (centers.size() < 4) return List.of();
        return interpolateAll(centers, config.density());
    }

    /**
     * Generates a full spline with default config. Convenience method.
     */
    public static List<Vector3D> generate(Collection<? extends PortalDTO> portals) {
        return generate(portals, SplineConfig.EASY);
    }

    private static List<Vector3D> interpolateAll(List<Vector3D> centers, double densityMultiplier) {
        int windowSize = Math.min(6, centers.size());
        var windows = WindowedStreamUtils.windowed(centers, windowSize);
        var result = new ArrayList<Vector3D>();

        for (var window : windows) {
            if (window.size() < 4) continue;

            double distance = window.getFirst().distanceSq(window.get(Math.min(2, window.size() - 1)));
            int pointsPerSegment = Math.max(3,
                    (int) ((BASE_POINTS_PER_SEGMENT * distance) * DISTANCE_FACTOR * densityMultiplier));

            result.addAll(SplineAPI.interpolate(window, 0, pointsPerSegment));

            if (window.size() >= 6) {
                result.addAll(SplineAPI.interpolate(window, 2, pointsPerSegment));
            }
        }

        return result;
    }

    private static List<Vector3D> extractSortedCenters(Collection<? extends PortalDTO> portals) {
        return portals.stream()
                .sorted(Comparator.comparingInt(PortalDTO::index))
                .map(portal -> portal.locations().stream()
                        .filter(LocationDTO::center)
                        .findFirst()
                        .orElse(null))
                .filter(loc -> loc != null)
                .map(loc -> Vector3D.of(loc.x() + 0.5, loc.y() + 0.5, loc.z() + 0.5))
                .toList();
    }
}
