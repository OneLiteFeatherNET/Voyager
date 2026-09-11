package net.elytrarace.voyager.api.race;

import net.elytrarace.voyager.api.math.Vec3;
import net.elytrarace.voyager.api.race.exception.InvalidMapException;

import java.time.Duration;
import java.util.List;

/**
 * A racecourse: where it starts, the world it lives in, the rings a player flies through in order,
 * and the time a lap must beat to set a new record.
 *
 * <p>The reference time (D-E3-4) replaces the old tree's rule that the first finisher on a fresh map
 * always earns the top medal because there is nothing yet to compare against — see the greenfield
 * design's decision table.
 *
 * <p>{@link #rings()} is guaranteed to be indexed {@code 0..n-1} in list order, matching the position
 * a {@code ProgressTracker} reads a ring by ({@code rings.get(passedCount)}). A map definition built
 * from anything else — a loader that sorted by file order rather than by index, say — would let the
 * game demand rings in the wrong sequence, so that guarantee is enforced here rather than trusted to
 * every caller.
 */
public record MapDefinition(String name, String world, Vec3 spawn, List<Ring> rings, Duration referenceTime) {

    public MapDefinition {
        if (name == null || name.isBlank()) {
            throw InvalidMapException.blankName(name);
        }
        if (world == null || world.isBlank()) {
            throw InvalidMapException.blankWorld(world);
        }
        if (rings.isEmpty()) {
            throw InvalidMapException.emptyRings();
        }
        rings = List.copyOf(rings);
        for (int i = 0; i < rings.size(); i++) {
            if (rings.get(i).index() != i) {
                throw InvalidMapException.ringsOutOfOrder();
            }
        }
        if (referenceTime.isZero() || referenceTime.isNegative()) {
            throw InvalidMapException.nonPositiveReferenceTime(referenceTime);
        }
    }
}
