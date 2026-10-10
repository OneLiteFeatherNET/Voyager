package net.elytrarace.voyager.api.race.exception;

import net.elytrarace.voyager.api.race.GuidePoint;

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

    public static InvalidMapException guideOutsideTheCourse(int orderIndex, int ringCount) {
        return new InvalidMapException(
                ("a guide point bends the line between two rings, so its order index must fall inside "
                        + "the course: %s is outside 0..%s, which is where this map's %s ring(s) sit. "
                        + "A guide in front of the first ring or past the last one would extend the "
                        + "line beyond the course rather than bend it")
                        .formatted(orderIndex, (ringCount - 1) * GuidePoint.RING_ORDER_STRIDE, ringCount));
    }

    public static InvalidMapException nonPositiveReferenceTime(Duration referenceTime) {
        return new InvalidMapException(
                "map reference time must be positive, was %s".formatted(referenceTime));
    }
}
