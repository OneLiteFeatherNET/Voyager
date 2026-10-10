package net.elytrarace.spline;

import org.apache.commons.geometry.euclidean.threed.Vector3D;

/**
 * A point on the ideal racing line. The spline passes through all points in order.
 * There is no mathematical distinction between types — the type is purely semantic metadata.
 *
 * <p>Points are ordered by {@code orderIndex}. Use gapped indices (0, 100, 200, ...)
 * so guide points can be inserted between portals without re-indexing.</p>
 */
public sealed interface PathPoint extends Comparable<PathPoint>
        permits PathPoint.SpawnPoint, PathPoint.PortalPoint, PathPoint.GuidePoint {

    Vector3D position();
    int orderIndex();

    @Override
    default int compareTo(PathPoint other) {
        return Integer.compare(this.orderIndex(), other.orderIndex());
    }

    /**
     * The starting point of the flight path. Exactly one per map.
     */
    record SpawnPoint(Vector3D position, int orderIndex) implements PathPoint {}

    /**
     * A mandatory waypoint — the spline must pass through the portal center.
     * Linked to a portal by index. Cannot be deleted (only the portal can be deleted).
     */
    record PortalPoint(Vector3D position, int orderIndex, int portalIndex) implements PathPoint {}

    /**
     * An optional shaping waypoint placed by the builder to route the path
     * around obstacles. Can be added, moved, and deleted freely.
     */
    record GuidePoint(Vector3D position, int orderIndex) implements PathPoint {}
}
