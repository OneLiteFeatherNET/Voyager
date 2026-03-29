package net.elytrarace.server.ecs.component;

import net.elytrarace.common.ecs.Component;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Tracks which ring checkpoints a player has passed through on the current map.
 */
public class RingTrackerComponent implements Component {

    private final Set<Integer> passedRings = new HashSet<>();

    /**
     * Returns whether the ring at the given index has already been passed.
     */
    public boolean hasPassed(int ringIndex) {
        return passedRings.contains(ringIndex);
    }

    /**
     * Marks the ring at the given index as passed.
     */
    public void markPassed(int ringIndex) {
        passedRings.add(ringIndex);
    }

    /**
     * Returns the number of rings passed so far.
     */
    public int passedCount() {
        return passedRings.size();
    }

    /**
     * Returns an unmodifiable view of the passed ring indices.
     */
    public Set<Integer> getPassedRings() {
        return Collections.unmodifiableSet(passedRings);
    }

    /**
     * Resets the tracker for a new map.
     */
    public void reset() {
        passedRings.clear();
    }
}
