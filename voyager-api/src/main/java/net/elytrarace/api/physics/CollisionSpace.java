package net.elytrarace.api.physics;

import net.elytrarace.api.math.Aabb;

import java.util.List;

/**
 * Read-only block-collision access, defined from the consumer's need rather than the provider's
 * capability.
 *
 * <p>The physics simulation is handed a collision space; it never looks one up. That is what keeps
 * the physics module free of any server dependency and lets the trace suite supply a recorded world
 * slice instead of a live instance.
 */
@FunctionalInterface
public interface CollisionSpace {

    /** Returns every solid box intersecting the region, in unspecified order. */
    List<Aabb> boxesIntersecting(Aabb region);

    /** A space containing nothing, for tests that only exercise free flight. */
    static CollisionSpace empty() {
        return region -> List.of();
    }
}
