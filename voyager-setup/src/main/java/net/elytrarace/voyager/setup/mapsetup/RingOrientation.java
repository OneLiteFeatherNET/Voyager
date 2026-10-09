package net.elytrarace.voyager.setup.mapsetup;

import net.elytrarace.voyager.api.math.Vec3;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Contract;

/**
 * The rotation that turns a disc's base axis onto a ring normal, as a unit quaternion.
 *
 * <p>The preview draws a ring as a thin block whose thin side is the base axis {@code +Z}. Pure maths, so the
 * preview adapter only copies the four numbers into the display entity.
 */
@ApiStatus.Internal
public abstract class RingOrientation {

    /** The thin axis of a disc model: a ring's normal before rotation. */
    public static final Vec3 BASE_AXIS = new Vec3(0, 0, 1);

    /** Below this distance from the antipode of the base axis, the rotation is taken about a fixed perpendicular. */
    private static final double ANTIPODE_TOLERANCE = 1e-12;

    private RingOrientation() {
    }

    /**
     * A unit quaternion {@code (x, y, z, w)}.
     *
     * @param x the vector part, first component
     * @param y the vector part, second component
     * @param z the vector part, third component
     * @param w the scalar part
     */
    public record Quaternion(double x, double y, double z, double w) {
    }

    /**
     * @param normal a unit normal
     * @return the unit quaternion that rotates {@link #BASE_AXIS} onto {@code normal}
     */
    @Contract(pure = true, value = "_ -> new")
    public static Quaternion fromNormal(Vec3 normal) {
        double dot = BASE_AXIS.dot(normal);
        if (dot < -1.0 + ANTIPODE_TOLERANCE) {
            // The opposite of the base axis: a half turn about an axis perpendicular to it.
            return new Quaternion(1, 0, 0, 0);
        }
        Vec3 axis = cross(BASE_AXIS, normal);
        return normalised(new Quaternion(axis.x(), axis.y(), axis.z(), 1.0 + dot));
    }

    /**
     * @param rotation a unit quaternion
     * @param vector   the vector to rotate
     * @return {@code vector} rotated by {@code rotation}
     */
    @Contract(pure = true, value = "_, _ -> new")
    public static Vec3 rotate(Quaternion rotation, Vec3 vector) {
        Vec3 u = new Vec3(rotation.x(), rotation.y(), rotation.z());
        Vec3 uv = cross(u, vector);
        Vec3 uuv = cross(u, uv);
        return vector.plus(uv.scale(2.0 * rotation.w())).plus(uuv.scale(2.0));
    }

    private static Vec3 cross(Vec3 a, Vec3 b) {
        return new Vec3(
                a.y() * b.z() - a.z() * b.y(),
                a.z() * b.x() - a.x() * b.z(),
                a.x() * b.y() - a.y() * b.x());
    }

    private static Quaternion normalised(Quaternion q) {
        double length = Math.sqrt(q.x() * q.x() + q.y() * q.y() + q.z() * q.z() + q.w() * q.w());
        return new Quaternion(q.x() / length, q.y() / length, q.z() / length, q.w() / length);
    }
}
