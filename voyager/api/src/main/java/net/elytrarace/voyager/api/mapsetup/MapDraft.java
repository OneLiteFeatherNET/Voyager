package net.elytrarace.voyager.api.mapsetup;

import net.elytrarace.voyager.api.math.Vec3;
import net.elytrarace.voyager.api.mapsetup.exception.InvalidDraftException;
import net.elytrarace.voyager.api.race.Author;
import net.elytrarace.voyager.api.race.BoostConfig;
import net.elytrarace.voyager.api.race.CatalogKeys;
import net.elytrarace.voyager.api.race.GuideLine;
import net.elytrarace.voyager.api.race.Ring;
import net.elytrarace.voyager.api.race.effect.RingEffectTuning;
import net.kyori.adventure.key.Key;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * A map as the builder has made it so far. It is deliberately not a {@code MapDefinition}: a draft may have no rings
 * and no spawn, and those are the problems the status command reports. A draft becomes game-loadable when it has a
 * spawn and at least one ring (ADR-0018).
 *
 * @param id                   the map's name and folder name
 * @param world                the world directory the map races in; the setup server writes it as the id
 * @param spawn                where a racer starts, or {@code null} while no spawn is set
 * @param rings                the rings in flight order; each ring's index is its position
 * @param referenceTimeSeconds the provisional reference time, finite and positive
 * @param boostConfig          the boost tuning of the map
 * @param guideLine            the racing line of the map
 * @param ringEffects          the velocity factors of the map's BOOST and SLOW rings
 * @param displayName          the name players read, or {@code null} when the draft states none
 * @param authors              the players who built the map, as the file states them
 */
public record MapDraft(MapId id, String world, @Nullable Vec3 spawn, List<Ring> rings, double referenceTimeSeconds,
                       BoostConfig boostConfig, GuideLine guideLine, RingEffectTuning ringEffects,
                       @Nullable String displayName, List<Author> authors) {

    /** A draft with the factors every map used before they were per map, and no display name or authors. */
    public MapDraft(MapId id, String world, @Nullable Vec3 spawn, List<Ring> rings, double referenceTimeSeconds,
                    BoostConfig boostConfig, GuideLine guideLine) {
        this(id, world, spawn, rings, referenceTimeSeconds, boostConfig, guideLine, RingEffectTuning.DEFAULT);
    }

    /** A draft with no display name and no authors. */
    public MapDraft(MapId id, String world, @Nullable Vec3 spawn, List<Ring> rings, double referenceTimeSeconds,
                    BoostConfig boostConfig, GuideLine guideLine, RingEffectTuning ringEffects) {
        this(id, world, spawn, rings, referenceTimeSeconds, boostConfig, guideLine, ringEffects, null, List.of());
    }

    /**
     * The key the map is saved under: {@code elytrarace:} followed by the folder name. The one place the draft's key is
     * computed, so the writer and the game's reader agree on it.
     */
    public Key key() {
        return CatalogKeys.of(id.value());
    }

    public MapDraft {
        if (world.isBlank()) {
            throw InvalidDraftException.blankWorld(id);
        }
        authors = Author.checked(authors);
        rings = List.copyOf(rings);
        for (int position = 0; position < rings.size(); position++) {
            int index = rings.get(position).index();
            if (index != position) {
                throw InvalidDraftException.ringOutOfPlace(id, position, index);
            }
        }
        if (!Double.isFinite(referenceTimeSeconds) || referenceTimeSeconds <= 0.0) {
            throw InvalidDraftException.nonPositiveReferenceTime(id, referenceTimeSeconds);
        }
    }
}
