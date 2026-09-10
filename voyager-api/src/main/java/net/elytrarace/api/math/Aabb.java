package net.elytrarace.api.math;

/** An immutable axis-aligned bounding box in world space, in blocks. */
public record Aabb(Vec3 min, Vec3 max) {

    public Aabb {
        if (min.x() > max.x() || min.y() > max.y() || min.z() > max.z()) {
            throw new InvalidBoundingBoxException(min, max);
        }
    }

    /** Touching faces count as intersecting, matching how Vanilla resolves block collision. */
    public boolean intersects(Aabb other) {
        return min.x() <= other.max.x() && max.x() >= other.min.x()
                && min.y() <= other.max.y() && max.y() >= other.min.y()
                && min.z() <= other.max.z() && max.z() >= other.min.z();
    }

    public Aabb expand(double amount) {
        Vec3 delta = new Vec3(amount, amount, amount);
        return new Aabb(min.minus(delta), max.plus(delta));
    }
}
