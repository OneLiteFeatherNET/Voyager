package net.elytrarace.voyager.physics.trace;

import net.elytrarace.voyager.api.math.Aabb;
import net.elytrarace.voyager.api.math.Vec3;
import net.elytrarace.voyager.api.physics.CollisionSpace;

import java.util.List;

/**
 * A {@link CollisionSpace} backed by a fixture's recorded world slice.
 *
 * <p>{@link #boxesIntersecting(Aabb)} filters the recorded blocks down to the ones that actually
 * intersect the queried region, matching every other {@link CollisionSpace} implementation in this
 * codebase. Returning the full world slice unconditionally would leave {@link
 * net.elytrarace.voyager.physics.collision.MovementResolver}'s swept-region logic unpinned by this
 * harness — a fixture that ignores the region argument cannot distinguish "the resolver queried the
 * right region" from "the resolver queried nothing at all and got lucky."
 */
final class RecordedCollisionSpace implements CollisionSpace {

    private final List<Aabb> blocks;

    RecordedCollisionSpace(List<TraceFixture.BlockBox> worldSlice) {
        this.blocks = worldSlice.stream().map(RecordedCollisionSpace::toAabb).toList();
    }

    @Override
    public List<Aabb> boxesIntersecting(Aabb region) {
        return blocks.stream().filter(region::intersects).toList();
    }

    private static Aabb toAabb(TraceFixture.BlockBox box) {
        return new Aabb(
                new Vec3(box.minX(), box.minY(), box.minZ()),
                new Vec3(box.maxX(), box.maxY(), box.maxZ()));
    }
}
