package net.elytrarace.voyager.physics;

import net.elytrarace.voyager.api.math.Aabb;
import net.elytrarace.voyager.api.math.Vec3;
import net.elytrarace.voyager.physics.collision.MovementResolver;
import net.elytrarace.voyager.physics.collision.MovementResult;

import java.util.List;

/**
 * A test-only second resolver that differs from {@link MovementResolver} in exactly one respect:
 * Vanilla's sub-{@code 1.0E-7} snap fires once per <em>shape</em> rather than once per box.
 *
 * <p>It exists to measure one thing, and is not a candidate to replace anything. Vanilla's
 * {@code Shapes.collide} iterates {@code Iterable<VoxelShape>} and puts the guard at the top of that
 * loop, so a block made of several boxes is one guard check there. {@link
 * net.elytrarace.voyager.api.physics.CollisionSpace} hands back boxes, so the same block is several
 * guard checks in {@link MovementResolver}. Whether that difference can move a trajectory is what
 * {@link ShapeFlatteningMeasurementTest} answers.
 *
 * <p>Everything else is transcribed from {@link MovementResolver} unchanged — the swept region, the
 * axis order, the per-axis clamp, the {@code Mth.equal} tolerance on the horizontal flags. That the
 * transcription is faithful is not assumed: with every shape a singleton the two algorithms are the
 * same algorithm, and {@link ShapeFlatteningMeasurementTest} asserts they agree tick for tick on a
 * world of full blocks before it measures anything.
 */
final class ShapeGroupedResolver {

    private static final double SWEEP_EPSILON = 1.0e-7;
    private static final float HORIZONTAL_EQUALITY_EPSILON = 1.0e-5F;
    private static final double SNAP_TO_ZERO_EPSILON = 1.0e-7;

    /** A collision space that keeps each block's boxes together instead of flattening them. */
    @FunctionalInterface
    interface ShapeSpace {

        /**
         * The shapes intersecting {@code region}, each as its own list of boxes, in the order
         * {@link net.elytrarace.voyager.api.physics.CollisionSpace} would emit them. A box that does
         * not intersect is dropped from its shape; a shape left with no box is dropped entirely —
         * the same per-box filter {@code MinestomCollisionSpace} applies.
         */
        List<List<Aabb>> shapesIntersecting(Aabb region);
    }

    private ShapeGroupedResolver() {
    }

    static MovementResult resolve(Aabb box, Vec3 movement, ShapeSpace space) {
        if (movement.lengthSquared() == 0.0) {
            return new MovementResult(Vec3.ZERO, false, false, false, false);
        }

        List<List<Aabb>> shapes = space.shapesIntersecting(sweptRegion(box, movement));

        double clampedY = clampY(box, shapes, movement.y());
        Aabb afterY = translate(box, 0.0, clampedY, 0.0);
        boolean verticalCollision = clampedY != movement.y();
        boolean onGround = movement.y() < 0.0 && verticalCollision;

        double clampedX;
        double clampedZ;
        if (Math.abs(movement.x()) < Math.abs(movement.z())) {
            clampedZ = clampZ(afterY, shapes, movement.z());
            Aabb afterZ = translate(afterY, 0.0, 0.0, clampedZ);
            clampedX = clampX(afterZ, shapes, movement.x());
        } else {
            clampedX = clampX(afterY, shapes, movement.x());
            Aabb afterX = translate(afterY, clampedX, 0.0, 0.0);
            clampedZ = clampZ(afterX, shapes, movement.z());
        }

        boolean xCollision = !withinHorizontalTolerance(clampedX, movement.x());
        boolean zCollision = !withinHorizontalTolerance(clampedZ, movement.z());

        return new MovementResult(
                new Vec3(clampedX, clampedY, clampedZ),
                xCollision, verticalCollision, zCollision, onGround);
    }

    private static double clampY(Aabb box, List<List<Aabb>> shapes, double dy) {
        double result = dy;
        for (List<Aabb> shape : shapes) {
            // Shapes.collide's guard, once per shape — the one line this class exists to move.
            if (Math.abs(result) < SNAP_TO_ZERO_EPSILON) {
                return 0.0;
            }
            for (Aabb other : shape) {
                if (!overlapsStrict(other.max().x(), other.min().x(), box.min().x(), box.max().x())
                        || !overlapsStrict(other.max().z(), other.min().z(), box.min().z(), box.max().z())) {
                    continue;
                }
                if (result > 0.0 && box.max().y() <= other.min().y()) {
                    double limit = other.min().y() - box.max().y();
                    if (limit < result) {
                        result = limit;
                    }
                } else if (result < 0.0 && box.min().y() >= other.max().y()) {
                    double limit = other.max().y() - box.min().y();
                    if (limit > result) {
                        result = limit;
                    }
                }
            }
        }
        return result;
    }

    private static double clampX(Aabb box, List<List<Aabb>> shapes, double dx) {
        double result = dx;
        for (List<Aabb> shape : shapes) {
            if (Math.abs(result) < SNAP_TO_ZERO_EPSILON) {
                return 0.0;
            }
            for (Aabb other : shape) {
                if (!overlapsStrict(other.max().y(), other.min().y(), box.min().y(), box.max().y())
                        || !overlapsStrict(other.max().z(), other.min().z(), box.min().z(), box.max().z())) {
                    continue;
                }
                if (result > 0.0 && box.max().x() <= other.min().x()) {
                    double limit = other.min().x() - box.max().x();
                    if (limit < result) {
                        result = limit;
                    }
                } else if (result < 0.0 && box.min().x() >= other.max().x()) {
                    double limit = other.max().x() - box.min().x();
                    if (limit > result) {
                        result = limit;
                    }
                }
            }
        }
        return result;
    }

    private static double clampZ(Aabb box, List<List<Aabb>> shapes, double dz) {
        double result = dz;
        for (List<Aabb> shape : shapes) {
            if (Math.abs(result) < SNAP_TO_ZERO_EPSILON) {
                return 0.0;
            }
            for (Aabb other : shape) {
                if (!overlapsStrict(other.max().x(), other.min().x(), box.min().x(), box.max().x())
                        || !overlapsStrict(other.max().y(), other.min().y(), box.min().y(), box.max().y())) {
                    continue;
                }
                if (result > 0.0 && box.max().z() <= other.min().z()) {
                    double limit = other.min().z() - box.max().z();
                    if (limit < result) {
                        result = limit;
                    }
                } else if (result < 0.0 && box.min().z() >= other.max().z()) {
                    double limit = other.max().z() - box.min().z();
                    if (limit > result) {
                        result = limit;
                    }
                }
            }
        }
        return result;
    }

    private static boolean withinHorizontalTolerance(double achieved, double requested) {
        return Math.abs(requested - achieved) < HORIZONTAL_EQUALITY_EPSILON;
    }

    private static Aabb sweptRegion(Aabb box, Vec3 movement) {
        Vec3 movedMin = box.min().plus(movement);
        Vec3 movedMax = box.max().plus(movement);
        Vec3 min = new Vec3(
                Math.min(box.min().x(), movedMin.x()),
                Math.min(box.min().y(), movedMin.y()),
                Math.min(box.min().z(), movedMin.z()));
        Vec3 max = new Vec3(
                Math.max(box.max().x(), movedMax.x()),
                Math.max(box.max().y(), movedMax.y()),
                Math.max(box.max().z(), movedMax.z()));
        return new Aabb(min, max).expand(SWEEP_EPSILON);
    }

    private static Aabb translate(Aabb box, double dx, double dy, double dz) {
        Vec3 delta = new Vec3(dx, dy, dz);
        return new Aabb(box.min().plus(delta), box.max().plus(delta));
    }

    private static boolean overlapsStrict(double otherMax, double otherMin, double boxMin, double boxMax) {
        return otherMax > boxMin && otherMin < boxMax;
    }
}
