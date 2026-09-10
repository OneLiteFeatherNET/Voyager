package net.elytrarace.voyager.physics.collision;

import net.elytrarace.voyager.api.math.Aabb;
import net.elytrarace.voyager.api.math.Vec3;
import net.elytrarace.voyager.api.physics.CollisionSpace;
import org.jetbrains.annotations.ApiStatus;

import java.util.List;

/**
 * Integrates an attempted movement of a box against the world's solid blocks — Vanilla's steps 9
 * and 10 of the per-tick movement pipeline, {@code Entity.move} calling into
 * {@code Entity.collide} / {@code AABB.collideX/Y/Z}.
 *
 * <p>The sweep is axis-separated. Y always resolves first, then whichever of X or Z has the larger
 * magnitude in {@code movement}, then the other — transcribed from
 * {@code Direction.axisStepOrder(Vec3)}: {@code Math.abs(movement.x) < Math.abs(movement.z) ?
 * YZX_AXIS_ORDER : YXZ_AXIS_ORDER}. Each axis is clamped against the box already moved by the
 * previous axes. Per axis, every candidate box that overlaps the moving box on the other two axes
 * is considered; among those, the movement is clamped to the nearest surface in the direction of
 * travel.
 *
 * <p><b>Step-up is not implemented.</b> Vanilla's {@code collide} also nudges the box upward by up
 * to {@code maxUpStep()} so an entity can climb a slab-height ledge without stopping, but that
 * branch is gated on {@code maxUpStep() > 0} <em>and</em> ({@code onGroundAfterCollision ||
 * onGround()}) <em>and</em> a horizontal collision on the same tick. A gliding entity is airborne
 * for the entire glide, so {@code onGround()} is false and the branch is unreachable on every tick
 * except the one that lands — and even then, only if a horizontal collision happens on that same
 * tick. This port implements the axis-separated sweep and not step-up; a passing suite here is not
 * proof that step-up was implemented. If a landing trace later diverges on its final tick, this
 * assumption was wrong and the branch must be ported.
 *
 * <p>Two further limits are inherited from how the fixtures backing this port were recorded: the
 * world slice a {@link CollisionSpace} exposes records unit cubes for solid blocks only — slabs,
 * stairs and fences are not represented — and entity-versus-entity collision is not simulated.
 */
@ApiStatus.Internal
public abstract class MovementResolver {

    /** Slack added around the swept region so a box moving exactly onto a surface still queries it. */
    private static final double SWEEP_EPSILON = 1.0e-7;

    /**
     * Vanilla's horizontal-collision tolerance, transcribed from {@code Mth.equal(double, double)}
     * — {@code Math.abs(b - a) < 1.0E-5F}. The vertical flag ({@link #resolve}'s {@code onGround})
     * uses no such tolerance; only X and Z do.
     */
    private static final float HORIZONTAL_EQUALITY_EPSILON = 1.0e-5F;

    private MovementResolver() {
    }

    /**
     * Resolves {@code movement} applied to {@code box} against {@code space}.
     *
     * <p>A zero movement returns immediately with a zero result and never queries {@code space} —
     * Vanilla short-circuits on {@code movement.lengthSqr() == 0.0}.
     */
    public static MovementResult resolve(Aabb box, Vec3 movement, CollisionSpace space) {
        if (movement.lengthSquared() == 0.0) {
            return new MovementResult(Vec3.ZERO, false, false, false, false, false);
        }

        List<Aabb> candidates = space.boxesIntersecting(sweptRegion(box, movement));

        double clampedY = clampY(box, candidates, movement.y());
        Aabb afterY = translate(box, 0.0, clampedY, 0.0);
        boolean verticalCollision = clampedY != movement.y();
        boolean onGround = movement.y() < 0.0 && verticalCollision;

        double clampedX;
        double clampedZ;
        if (Math.abs(movement.x()) < Math.abs(movement.z())) {
            // Direction.axisStepOrder: more Z speed than X speed resolves Z before X.
            clampedZ = clampZ(afterY, candidates, movement.z());
            Aabb afterZ = translate(afterY, 0.0, 0.0, clampedZ);
            clampedX = clampX(afterZ, candidates, movement.x());
        } else {
            clampedX = clampX(afterY, candidates, movement.x());
            Aabb afterX = translate(afterY, clampedX, 0.0, 0.0);
            clampedZ = clampZ(afterX, candidates, movement.z());
        }

        boolean xCollision = clampedX != movement.x();
        boolean zCollision = clampedZ != movement.z();
        boolean horizontalCollision = !withinHorizontalTolerance(clampedX, movement.x())
                || !withinHorizontalTolerance(clampedZ, movement.z());

        return new MovementResult(
                new Vec3(clampedX, clampedY, clampedZ),
                xCollision, verticalCollision, zCollision, horizontalCollision, onGround);
    }

    /** {@code Mth.equal(achieved, requested)}: {@code Math.abs(requested - achieved) < 1.0E-5F}. */
    private static boolean withinHorizontalTolerance(double achieved, double requested) {
        return Math.abs(requested - achieved) < HORIZONTAL_EQUALITY_EPSILON;
    }

    /** The region the box sweeps through while moving, expanded by a small epsilon. */
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

    private static double clampY(Aabb box, List<Aabb> candidates, double dy) {
        double result = dy;
        for (Aabb other : candidates) {
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
        return result;
    }

    private static double clampX(Aabb box, List<Aabb> candidates, double dx) {
        double result = dx;
        for (Aabb other : candidates) {
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
        return result;
    }

    private static double clampZ(Aabb box, List<Aabb> candidates, double dz) {
        double result = dz;
        for (Aabb other : candidates) {
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
        return result;
    }

    /**
     * Strict overlap on one axis, matching {@link Aabb#intersects(Aabb)}: two ranges that merely
     * touch at a boundary do not overlap.
     */
    private static boolean overlapsStrict(double otherMax, double otherMin, double boxMin, double boxMax) {
        return otherMax > boxMin && otherMin < boxMax;
    }
}
