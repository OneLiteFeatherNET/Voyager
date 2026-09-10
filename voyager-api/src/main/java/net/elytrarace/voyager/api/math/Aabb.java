package net.elytrarace.voyager.api.math;

import net.elytrarace.voyager.api.math.exception.InvalidBoundingBoxException;

/** An immutable axis-aligned bounding box in world space, in blocks. */
public record Aabb(Vec3 min, Vec3 max) {

    public Aabb {
        if (min.x() > max.x() || min.y() > max.y() || min.z() > max.z()) {
            throw new InvalidBoundingBoxException(min, max);
        }
    }

    /**
     * Tests strict overlap on all three axes: two boxes that merely touch along a face, an edge or a
     * corner do <em>not</em> intersect. This is Vanilla's semantics, verified against the decompiled
     * {@code net/minecraft/world/phys/AABB.java:235} for Minecraft 26.2 — see
     * {@code docs/reference/elytra-physics-26.2.md}.
     */
    public boolean intersects(Aabb other) {
        return min.x() < other.max.x() && max.x() > other.min.x()
                && min.y() < other.max.y() && max.y() > other.min.y()
                && min.z() < other.max.z() && max.z() > other.min.z();
    }

    public Aabb expand(double amount) {
        Vec3 delta = new Vec3(amount, amount, amount);
        return new Aabb(min.minus(delta), max.plus(delta));
    }
}
