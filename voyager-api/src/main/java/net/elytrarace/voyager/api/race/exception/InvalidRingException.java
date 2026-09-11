package net.elytrarace.voyager.api.race.exception;

import net.elytrarace.voyager.api.math.Vec3;

/** Thrown when a {@code Ring} is constructed with a value that would make it unpassable or ambiguous. */
public final class InvalidRingException extends RuntimeException {

    private InvalidRingException(String message) {
        super(message);
    }

    public static InvalidRingException negativeIndex(int index) {
        return new InvalidRingException("ring index must not be negative, was %d".formatted(index));
    }

    public static InvalidRingException nonPositiveRadius(double radius) {
        return new InvalidRingException("ring radius must be positive, was %s".formatted(radius));
    }

    public static InvalidRingException negativePoints(int points) {
        return new InvalidRingException("ring points must not be negative, was %d".formatted(points));
    }

    public static InvalidRingException normalNotUnitLength(Vec3 normal) {
        return new InvalidRingException(
                "ring normal must be unit length, was %s with length %s".formatted(normal, normal.length()));
    }
}
