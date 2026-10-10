package net.elytrarace.voyager.api.physics;

import net.elytrarace.voyager.api.math.Aabb;

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

    /**
     * Returns every solid box intersecting the region.
     *
     * <p><b>Order matters.</b> Vanilla clamps each axis by iterating candidate shapes and
     * short-circuiting to exactly zero as soon as the remaining distance falls under
     * {@code 1.0E-7} ({@code Shapes.collide}). Whether a sub-epsilon residual snaps therefore
     * depends on which candidate is visited next, and the port reproduces that. An
     * implementation must return boxes in the same order Vanilla's block iteration produces
     * them; returning them in an arbitrary order makes the snap non-deterministic.
     *
     * <p>This is a known open point for the platform layer: the Minestom-backed implementation
     * has to establish what that order is rather than assume its own.
     */
    List<Aabb> boxesIntersecting(Aabb region);

    /** A space containing nothing, for tests that only exercise free flight. */
    static CollisionSpace empty() {
        return region -> List.of();
    }
}
