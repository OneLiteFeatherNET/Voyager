package net.elytrarace.common.collision;

import net.elytrarace.common.map.model.LocationDTO;
import org.apache.commons.geometry.euclidean.threed.Planes;
import org.apache.commons.geometry.euclidean.threed.Bounds3D;
import org.apache.commons.geometry.euclidean.threed.Vector3D;
import org.apache.commons.geometry.euclidean.threed.line.Lines3D;
import org.apache.commons.geometry.euclidean.threed.shape.Parallelepiped;
import org.apache.commons.geometry.euclidean.threed.Plane;
import org.apache.commons.geometry.euclidean.threed.RegionBSPTree3D;
import org.apache.commons.numbers.core.Precision;

import java.util.List;
import java.util.function.Predicate;

/**
 * Shared collision detection for polyhedral portals using commons-geometry.
 * Used by both the game plugin's CollisionSystem and the setup plugin's testfly.
 */
public final class PortalCollisionHelper {

    public static final Precision.DoubleEquivalence PRECISION =
            Precision.doubleEquivalenceOfEpsilon(1e-6);

    private PortalCollisionHelper() {}

    /**
     * Constructs a collision-ready portal from LocationDTO vertices.
     */
    public static PortalGeometry buildGeometry(List<LocationDTO> locations) {
        var vertices = locations.stream()
                .filter(Predicate.not(LocationDTO::center))
                .map(loc -> Vector3D.of(loc.x(), loc.y(), loc.z()))
                .toList();

        if (vertices.size() < 3) {
            throw new IllegalArgumentException("A portal requires at least 3 non-center vertices");
        }

        var plane = Planes.fromPoints(vertices, PRECISION);
        var bounds = Bounds3D.from(vertices);
        var tree = Parallelepiped.fromBounds(plane).toTree();

        return new PortalGeometry(plane, bounds, tree);
    }

    /**
     * Checks if a movement path (3 consecutive positions) intersects a portal.
     * Uses 3 line segments (pos1→pos2, pos2→pos3, pos1→pos3) for robust detection.
     *
     * @param geometry the portal's collision geometry
     * @param pos1     position at tick N-2
     * @param pos2     position at tick N-1
     * @param pos3     position at tick N (current)
     * @return true if any segment intersects the portal
     */
    public static boolean checkIntersection(PortalGeometry geometry, Vector3D pos1, Vector3D pos2, Vector3D pos3) {
        return checkSegment(geometry, pos1, pos2)
                || checkSegment(geometry, pos2, pos3)
                || checkSegment(geometry, pos1, pos3);
    }

    /**
     * Checks if a single line segment intersects the portal.
     */
    public static boolean checkSegment(PortalGeometry geometry, Vector3D from, Vector3D to) {
        if (from.eq(to, PRECISION)) return false;

        var line = Lines3D.fromPoints(from, to, PRECISION);
        var segment = Lines3D.segmentFromPoints(from, to, PRECISION);
        var intersection = geometry.plane().intersection(line);

        return intersection != null
                && geometry.bounds().contains(intersection, PRECISION)
                && segment.contains(intersection)
                && geometry.tree().contains(intersection);
    }

    /**
     * Pre-computed collision geometry for a portal.
     */
    public record PortalGeometry(Plane plane, Bounds3D bounds, RegionBSPTree3D tree) {}
}
