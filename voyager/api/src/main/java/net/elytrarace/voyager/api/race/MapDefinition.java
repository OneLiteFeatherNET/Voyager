package net.elytrarace.voyager.api.race;

import net.elytrarace.voyager.api.math.Vec3;
import net.elytrarace.voyager.api.race.effect.RingEffectTuning;
import net.elytrarace.voyager.api.race.exception.InvalidMapException;

import net.kyori.adventure.key.Key;

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
 *
 * <p>{@link #boostConfig()} is per map rather than per server because a course decides how much a
 * rocket is worth on it: a 380-block climb and a flat sprint want different answers, and the old tree
 * already stored the tuning in the map file for that reason. Its own invariants — including the one
 * that keeps two rockets from ever burning on one racer at once — live in {@link BoostConfig}.
 *
 * <p>{@link #guideLine()} carries the control points that bend the racing line away from the terrain
 * between two rings, and how much of that line a racer is shown. A map with no guide points at all is
 * legal — the line is then the rings alone — but the bracketing rule is not optional and is checked
 * here rather than in {@link GuideLine}, for the reason {@link BoostConfig}'s cooldown rule is
 * checked in {@code BoostConfig}: this is the only type that can see both the guides and the rings
 * they claim to sit between.
 *
 * <p>{@link #ringEffects()} holds the velocity factors of this course's {@code BOOST} and {@code SLOW}
 * rings. A map that states none has {@link RingEffectTuning#DEFAULT}, the factors every course used
 * before they were per map.
 *
 * <p>{@link #key()} is the map's identifier, the key of the catalogue and of the statistics (ADR-0040).
 * {@link #displayName()} is the name players read; a blank or absent one is the key's value in title case, so it is never
 * null. {@link #authors()} are the players who built the map, if any.
 */
public record MapDefinition(Key key, String world, Vec3 spawn, List<Ring> rings, Duration referenceTime,
        BoostConfig boostConfig, GuideLine guideLine, RingEffectTuning ringEffects, String displayName,
        List<Author> authors) {

    /**
     * The map built from a flat id, which is read as the key {@code elytrarace:<name>}, with the
     * {@link RingEffectTuning#DEFAULT} factors, no display name and no authors.
     */
    public MapDefinition(String name, String world, Vec3 spawn, List<Ring> rings, Duration referenceTime,
            BoostConfig boostConfig, GuideLine guideLine) {
        this(name, world, spawn, rings, referenceTime, boostConfig, guideLine, RingEffectTuning.DEFAULT, null);
    }

    /** The map built from a flat id before ring effects were per map: no display name and no authors. */
    public MapDefinition(String name, String world, Vec3 spawn, List<Ring> rings, Duration referenceTime,
            BoostConfig boostConfig, GuideLine guideLine, RingEffectTuning ringEffects) {
        this(name, world, spawn, rings, referenceTime, boostConfig, guideLine, ringEffects, null);
    }

    /** The map built from a flat id, with its display name and no authors. */
    public MapDefinition(String name, String world, Vec3 spawn, List<Ring> rings, Duration referenceTime,
            BoostConfig boostConfig, GuideLine guideLine, RingEffectTuning ringEffects, String displayName) {
        this(flatKey(name), world, spawn, rings, referenceTime, boostConfig, guideLine, ringEffects, displayName,
                List.of());
    }

    public MapDefinition {
        if (key == null) {
            throw InvalidMapException.blankName(null);
        }
        CatalogKeys.checked(key);
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
        int lastGap = rings.size() - 2;
        for (GuidePoint guide : guideLine.points()) {
            if (guide.afterRing() < 0 || guide.afterRing() > lastGap) {
                throw InvalidMapException.guideOutsideTheCourse(guide.orderIndex(), rings.size());
            }
        }
        displayName = displayName == null || displayName.isBlank() ? CatalogKeys.titleCase(key) : displayName;
        authors = authors == null ? List.of() : Author.checked(authors);
    }

    /** The flat id as its key, or {@code null} for a blank id, which the canonical constructor refuses as a blank name. */
    private static Key flatKey(String name) {
        return name == null || name.isBlank() ? null : CatalogKeys.of(name);
    }
}
