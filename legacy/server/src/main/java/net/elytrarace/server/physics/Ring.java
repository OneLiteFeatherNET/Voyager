package net.elytrarace.server.physics;

import net.minestom.server.coordinate.Vec;

/**
 * Represents a ring checkpoint in 3D space for elytra racing.
 *
 * @param center the center point of the ring
 * @param normal the normal vector of the ring plane (should be normalized)
 * @param radius the radius of the ring
 * @param points the number of points awarded for passing through this ring
 * @param type   the type of ring determining its gameplay effect
 */
public record Ring(Vec center, Vec normal, double radius, int points, RingType type) {

    /**
     * Creates a ring with {@link RingType#STANDARD} type for backward compatibility.
     */
    public Ring(Vec center, Vec normal, double radius, int points) {
        this(center, normal, radius, points, RingType.STANDARD);
    }
}
