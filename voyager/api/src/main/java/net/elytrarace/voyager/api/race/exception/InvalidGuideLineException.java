package net.elytrarace.voyager.api.race.exception;

import net.elytrarace.voyager.api.race.GuideLine;
import net.elytrarace.voyager.api.race.GuidePoint;

/**
 * Thrown when a {@code GuideLine} or one of its {@code GuidePoint}s is constructed with a value that
 * would make the racing line ambiguous or unaffordable to draw.
 */
public final class InvalidGuideLineException extends RuntimeException {

    private InvalidGuideLineException(String message) {
        super(message);
    }

    public static InvalidGuideLineException onARingsOwnSlot(int orderIndex) {
        return new InvalidGuideLineException(
                ("a guide point must sit between two rings, but order index %s is ring %s's own slot "
                        + "(rings occupy multiples of %s), so nothing says whether the line reaches the "
                        + "guide or the ring first")
                        .formatted(orderIndex, orderIndex / GuidePoint.RING_ORDER_STRIDE,
                                GuidePoint.RING_ORDER_STRIDE));
    }

    public static InvalidGuideLineException duplicateOrderIndex(int orderIndex) {
        return new InvalidGuideLineException(
                ("two guide points share the order index %s; more than one guide between the same pair "
                        + "of rings is legal, but they need distinct indices to have an order at all")
                        .formatted(orderIndex));
    }

    public static InvalidGuideLineException nonPositiveLookAhead(int lookAheadRings) {
        return new InvalidGuideLineException(
                ("a guide line must reach at least one ring ahead of the one a racer is heading for, "
                        + "was %s; a line that reaches nowhere is a line nobody can follow")
                        .formatted(lookAheadRings));
    }

    public static InvalidGuideLineException particleSpacingTooFine(double particleSpacing) {
        return new InvalidGuideLineException(
                ("particle spacing must be a finite %s blocks or more, was %s; below that one racer's "
                        + "stretch of line costs more packets per refresh than a server should spend "
                        + "on it")
                        .formatted(GuideLine.MINIMUM_PARTICLE_SPACING, particleSpacing));
    }
}
