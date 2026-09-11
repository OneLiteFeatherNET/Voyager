package net.elytrarace.voyager.api.race.exception;

import java.time.Duration;

/** Thrown when a {@code MapDefinition} is constructed with a value that would make it unplayable. */
public final class InvalidMapException extends RuntimeException {

    private InvalidMapException(String message) {
        super(message);
    }

    public static InvalidMapException blankName(String name) {
        return new InvalidMapException("map name must not be blank, was '%s'".formatted(name));
    }

    public static InvalidMapException blankWorld(String world) {
        return new InvalidMapException("map world must not be blank, was '%s'".formatted(world));
    }

    public static InvalidMapException emptyRings() {
        return new InvalidMapException("map rings must not be empty");
    }

    public static InvalidMapException ringsOutOfOrder() {
        return new InvalidMapException(
                "map rings must be indexed 0..n-1 in list order, matching the position a "
                        + "ProgressTracker reads by rings.get(passedCount)");
    }

    public static InvalidMapException nonPositiveReferenceTime(Duration referenceTime) {
        return new InvalidMapException(
                "map reference time must be positive, was %s".formatted(referenceTime));
    }
}
