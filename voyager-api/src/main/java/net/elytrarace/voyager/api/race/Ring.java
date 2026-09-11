package net.elytrarace.voyager.api.race;

import net.elytrarace.voyager.api.math.Vec3;
import net.elytrarace.voyager.api.race.exception.InvalidRingException;

/**
 * A disc a player scores by flying through: the hit area is the circumcircle of the ring's corner
 * points, and the rim is inclusive.
 *
 * <p>The normal is stored rather than derived from two edge vectors of the ring's geometry — see the
 * greenfield design's decision table (D-E3-5) — so it must already be unit length by the time a
 * {@code Ring} is constructed.
 */
public record Ring(int index, Vec3 center, Vec3 normal, double radius, int points, RingType type) {

    private static final double UNIT_LENGTH_TOLERANCE = 1e-9;

    public Ring {
        if (index < 0) {
            throw InvalidRingException.negativeIndex(index);
        }
        if (radius <= 0.0) {
            throw InvalidRingException.nonPositiveRadius(radius);
        }
        if (points < 0) {
            throw InvalidRingException.negativePoints(points);
        }
        if (Math.abs(normal.length() - 1.0) > UNIT_LENGTH_TOLERANCE) {
            throw InvalidRingException.normalNotUnitLength(normal);
        }
    }
}
