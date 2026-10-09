package net.elytrarace.voyager.setup.mapsetup.exception;

import net.elytrarace.voyager.api.math.Vec3;

/** Thrown when a builder's look direction cannot give a ring a normal: zero length, or a length that overflows. */
public final class InvalidPoseException extends RuntimeException {

    private InvalidPoseException(String message) {
        super(message);
    }

    public static InvalidPoseException degenerateLook(Vec3 look) {
        return new InvalidPoseException(
                "the look direction %s has no usable length, so no ring normal can be taken from it".formatted(look));
    }
}
