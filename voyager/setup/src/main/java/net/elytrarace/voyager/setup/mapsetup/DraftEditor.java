package net.elytrarace.voyager.setup.mapsetup;

import net.elytrarace.voyager.api.math.Vec3;
import net.elytrarace.voyager.api.mapsetup.MapDraft;
import net.elytrarace.voyager.api.mapsetup.MapId;
import net.elytrarace.voyager.api.race.Ring;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Contract;

import java.util.ArrayList;
import java.util.List;

/**
 * The only edits this slice makes to a draft: append a ring, remove a ring, set the spawn. Each returns a new draft,
 * so the caller can keep the previous one until the save has succeeded.
 */
@ApiStatus.Internal
public abstract class DraftEditor {

    private DraftEditor() {
    }

    /**
     * @param id the map's id
     * @return a draft with the world named after the id, no spawn, no rings and the default tuning
     */
    @Contract(pure = true, value = "_ -> new")
    public static MapDraft skeleton(MapId id) {
        return new MapDraft(id, id.value(), null, List.of(), RingDefaults.REFERENCE_TIME_SECONDS,
                RingDefaults.BOOST, RingDefaults.GUIDE_LINE);
    }

    /**
     * @throws IllegalArgumentException if the ring's index is not the number of rings already in the draft
     */
    @Contract(pure = true, value = "_, _ -> new")
    public static MapDraft withRingAppended(MapDraft draft, Ring ring) {
        int next = draft.rings().size();
        if (ring.index() != next) {
            throw new IllegalArgumentException(
                    "a ring appended to a draft of %d rings must have index %d, was %d"
                            .formatted(next, next, ring.index()));
        }
        List<Ring> rings = new ArrayList<>(draft.rings());
        rings.add(ring);
        return with(draft, rings, draft.spawn());
    }

    /**
     * Removes the ring at {@code index} and renumbers every later ring, so that each index again equals its position.
     *
     * @throws IllegalArgumentException if {@code index} is not the index of a ring in the draft
     */
    @Contract(pure = true, value = "_, _ -> new")
    public static MapDraft withoutRing(MapDraft draft, int index) {
        if (index < 0 || index >= draft.rings().size()) {
            throw new IllegalArgumentException(
                    "no ring %d in a draft of %d rings".formatted(index, draft.rings().size()));
        }
        List<Ring> rings = new ArrayList<>(draft.rings().size() - 1);
        for (Ring ring : draft.rings()) {
            if (ring.index() == index) {
                continue;
            }
            int position = rings.size();
            rings.add(new Ring(position, ring.center(), ring.normal(), ring.radius(), ring.points(), ring.type()));
        }
        return with(draft, rings, draft.spawn());
    }

    @Contract(pure = true, value = "_, _ -> new")
    public static MapDraft withSpawn(MapDraft draft, Vec3 spawn) {
        return with(draft, draft.rings(), spawn);
    }

    private static MapDraft with(MapDraft draft, List<Ring> rings, Vec3 spawn) {
        return new MapDraft(draft.id(), draft.world(), spawn, rings, draft.referenceTimeSeconds(),
                draft.boostConfig(), draft.guideLine());
    }
}
