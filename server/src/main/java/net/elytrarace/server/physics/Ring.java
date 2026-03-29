package net.elytrarace.server.physics;

import net.minestom.server.coordinate.Vec;

/**
 * Represents a ring checkpoint in 3D space for elytra racing.
 *
 * @param center the center point of the ring
 * @param normal the normal vector of the ring plane (should be normalized)
 * @param radius the radius of the ring
 * @param points the number of points awarded for passing through this ring
 */
public record Ring(Vec center, Vec normal, double radius, int points) {}
