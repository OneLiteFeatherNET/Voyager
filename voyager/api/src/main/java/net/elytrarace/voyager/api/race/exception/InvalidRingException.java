package net.elytrarace.voyager.api.race.exception;

import net.elytrarace.voyager.api.math.Vec3;
import net.elytrarace.voyager.api.race.RingShape;

/** Thrown when a {@code Ring} is constructed with a value that would make it unpassable or ambiguous. */
public final class InvalidRingException extends RuntimeException {

    private InvalidRingException(String message) {
        super(message);
    }

    /**
     * Names the ring that a file states wrongly, so that a refusal read from the file says where it is.
     *
     * @param file     the file's description for messages, such as {@code "map 'sky-drift'"}
     * @param position the zero-based position of the ring in the file's {@code rings} array
     * @param cause    the refusal the ring's own values caused
     */
    public static InvalidRingException atPosition(String file, int position, InvalidRingException cause) {
        InvalidRingException refusal = new InvalidRingException(
                "%s, ring at position %s: %s".formatted(file, position, cause.getMessage()));
        refusal.initCause(cause);
        return refusal;
    }

    public static InvalidRingException negativeIndex(int index) {
        return new InvalidRingException("ring index must not be negative, was %d".formatted(index));
    }

    /**
     * @param field the radius that is wrong: {@code radius} for a circle or the semi-major radius of an oval,
     *              {@code radiusMinor} for the semi-minor radius of an oval
     * @param value the value that was given
     */
    public static InvalidRingException radiusOutOfRange(String field, double value) {
        return new InvalidRingException("ring %s must be within [%s, %s] blocks, was %s"
                .formatted(field, RingShape.MIN_RADIUS, RingShape.MAX_RADIUS, value));
    }

    public static InvalidRingException pointsOutOfRange(int points) {
        return new InvalidRingException("ring points must be within [0, 100], was %d".formatted(points));
    }

    public static InvalidRingException minorRadiusAboveMajor(double radiusMinor, double radius) {
        return new InvalidRingException(
                "ring radiusMinor must not exceed radius, was %s above %s".formatted(radiusMinor, radius));
    }

    public static InvalidRingException majorAxisNotUnitLength(Vec3 majorAxis) {
        return new InvalidRingException(
                "ring majorAxis must be unit length, was %s with length %s".formatted(majorAxis, majorAxis.length()));
    }

    public static InvalidRingException majorAxisNotPerpendicular(Vec3 majorAxis, Vec3 normal) {
        return new InvalidRingException(
                "ring majorAxis must be perpendicular to normal, was %s against normal %s"
                        .formatted(majorAxis, normal));
    }

    public static InvalidRingException normalNotUnitLength(Vec3 normal) {
        return new InvalidRingException(
                "ring normal must be unit length, was %s with length %s".formatted(normal, normal.length()));
    }
}
