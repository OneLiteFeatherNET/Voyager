package net.elytrarace.api.math.exception;

import net.elytrarace.api.math.Vec3;

/** Thrown when a bounding box is constructed with a minimum corner that exceeds its maximum. */
public final class InvalidBoundingBoxException extends RuntimeException {

    public InvalidBoundingBoxException(Vec3 min, Vec3 max) {
        super("bounding box minimum must not exceed its maximum on any axis, was %s to %s".formatted(min, max));
    }
}
