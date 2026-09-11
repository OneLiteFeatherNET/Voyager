package net.elytrarace.voyager.api.math;

import net.elytrarace.voyager.api.math.exception.NonFiniteVectorException;

/**
 * An immutable three-dimensional vector in world space, in blocks.
 *
 * <p>Components are always finite. Construction of a vector holding NaN or infinity fails.
 */
public record Vec3(double x, double y, double z) {

    public static final Vec3 ZERO = new Vec3(0, 0, 0);

    public Vec3 {
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) {
            throw new NonFiniteVectorException(x, y, z);
        }
    }

    public Vec3 plus(Vec3 other) {
        return new Vec3(x + other.x, y + other.y, z + other.z);
    }

    public Vec3 minus(Vec3 other) {
        return new Vec3(x - other.x, y - other.y, z - other.z);
    }

    public Vec3 scale(double factor) {
        return new Vec3(x * factor, y * factor, z * factor);
    }

    public double lengthSquared() {
        return x * x + y * y + z * z;
    }

    public double length() {
        return Math.sqrt(lengthSquared());
    }
}
